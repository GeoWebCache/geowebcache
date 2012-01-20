package org.geowebcache.seed;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileRange;

public class SeedEstimator {

    private static Log log = LogFactory.getLog(org.geowebcache.seed.SeedEstimator.class);

    TileBreeder seeder = null;
    
    /**
     * helper for counting the number of tiles in a TileRange
	 * Passes the call through to a more parameterised tileCount method.
     * 
     * @param TileRange collection of bounts, start and stop zooms
     * @return -1 if too many
     */
    public long tileCount(TileRange tr) {
        //TODO: Noticed that metatiling isn't always factored into the provided tile range
        long[][] coveredGridLevels = new long[tr.getZoomStop() - tr.getZoomStart() + 1][];

        for (int i = tr.getZoomStart(); i <= tr.getZoomStop(); i++) {
            long[] gridBounds = tr.rangeBounds(i);
            coveredGridLevels[i - tr.getZoomStart()] = gridBounds;
        }

        return tileCount(coveredGridLevels, tr.getZoomStart(), tr.getZoomStop());
    }

    /**
     * helper for counting the number of tiles
     * 
     * @param layer
     * @param level
     * @param gridBounds
     * @return -1 if too many
     */
    public long tileCount(long[][] coveredGridLevels, int startZoom, int stopZoom) {
        long count = 0;

        for (int i = startZoom; i <= stopZoom; i++) {
            // we need to look up the zoom level. coveredGridLevels may be an array 
            // that goes from zoom 3 to 5 so we have to look up the number stored 
            // with the grid bounds.
            for(int j = 0; j < coveredGridLevels.length; j++) {
                if(coveredGridLevels[i][4] == j) {
                    long[] gridBounds = coveredGridLevels[i];
        
                    long thisLevel = (1 + gridBounds[2] - gridBounds[0])
                            * (1 + gridBounds[3] - gridBounds[1]);
        
                    if (thisLevel > (Long.MAX_VALUE / 4) && i != stopZoom) {
                        return -1;
                    } else {
                        count += thisLevel;
                    }
                    break;
                }
            }
        }

        return count;
    }
    
    /**
     * Helper for counting the number of tiles
     * Can only be used if seeder has been set - either manually or due to using a spring instantiated copy of this class.
     * 
     * @param layerName
     * @param gridSetId
     * @param bounds
     * @param zoomStart
     * @param zoomStop
     * @return
     * @throws GeoWebCacheException
     */
    public long tileCount(String layerName, String gridSetId, BoundingBox bounds, int zoomStart, int zoomStop) throws GeoWebCacheException {
        if(seeder == null) {
            throw new GeoWebCacheException("Seeder not available (probably shouldn't have called tileCount with a layerName)");
        }

        TileLayer tl = seeder.findTileLayer(layerName);

        if (gridSetId == null) {
            gridSetId = tl.getGridSubsets().iterator().next();
        }
        GridSubset gridSubset = tl.getGridSubset(gridSetId);
        
        long[][] coveredGridLevels;

        if (bounds == null) {
            coveredGridLevels = gridSubset.getCoverages();
        } else {
            coveredGridLevels = gridSubset.getCoverageIntersections(bounds);
        }

        return tileCount(coveredGridLevels, zoomStart, zoomStop);
    }
    
    /**
     * Estimate how much disk space will be taken up with tiles given a set number of tiles
     * @param layerName
     * @param tileCount
     * @param format
     * @return
     * @throws GeoWebCacheException
     */
    public long diskSpaceEstimate(String layerName, long tileCount, String format) {
        return(tileCount * getTileSizeEstimate(layerName, format));
    }
    
    /**
     * Provides a rough estimate of tile size for a layer
     * Intended to represent the average size of a tile for the layer across all zoom levels.
     * Currently doesn't consider the layer, only the format. Later on should consider 
     * palette and compression information as well as any existing tiles.
     * @param layerName
     * @param format
     * @return
     * @throws GeoWebCacheException
     */
    private int getTileSizeEstimate(String layerName, String format) {
        MimeType mt;
        // if(seeder == null) {
            // we do an estimate without the layer information at this point
            // but we will likely want to incorporate it in the future
        // } else {
            // TileLayer tl = seeder.findTileLayer(layerName);
            
            // we won't worry about checking the format modifiers in the estimate yet
            // palette size and level of compression to apply would be useful in 
            // improving the accuracy of this estimate. For now assumes jpeg is raster 
            // that's possibly satellite imagery and png is linework / vectors
            
            // FormatModifier fm = tl.getFormatModifier(mt);
        // }

        if(format == null) {
            mt = ImageMime.png;
        } else {
            try {
                mt = MimeType.createFromFormat(format);
            } catch (MimeException e) {
                // couldn't parse the mime format
                mt = ImageMime.png;
            }
        }
        
        int kbPerTile;
        if(mt == ImageMime.jpeg) {
            // jpeg is BETTER compression than others (assuming some lossyness) 
            // but due to this is often used for imagery which leads to larger image files.
            kbPerTile = 25;
        } else if(mt == ImageMime.png8) {
            kbPerTile = 20;
        } else if(mt == ImageMime.png || mt == ImageMime.png24) {
            kbPerTile = 30;
        } else {
            // anything else we'll assume 20k
            kbPerTile = 20;
        }
        
        return kbPerTile;
    }

    /**
     * Estimate the total amount of time a seeding job will take.
     * Estimate will be adjusted based on how long it's taken so far and how many tiles have been done in that time.
     * 
     * @param timeSpent time spent in seconds on tiling so far
     * @param tilesDone Number of tiles done so far
     * @param tilesTotal Total tiles to be seeded
     * @param threadCount Number of threads that will concurrently generate tiles
     * @return Time in seconds left to complete the seeding
     */
    public long totalTimeEstimate(long timeSpent, long tilesDone, long tilesTotal, long threadCount) {
        return Math.round((double) timeSpent * (((double) tilesTotal / threadCount) / (double) tilesDone));
    }
    
    /**
     * Initial estimate of the total amount of time a seeding job will take.
     * Makes assumptions on how long tiles take to generate (5 a second).
     * @param tilesTotal Total tiles to be seeded
     * @param threadCount Number of threads that will concurrently generate tiles
     * @return Time in seconds left to complete the seeding
     */
    public long totalTimeEstimate(long tilesTotal, long threadCount) {
        return totalTimeEstimate(1, 5, tilesTotal, threadCount);
    }

    public void performEstimate(SeedEstimate estimate) throws GeoWebCacheException {
        estimate.tilesTotal = tileCount(estimate.layerName, estimate.gridSetId, estimate.bounds, estimate.zoomStart, estimate.zoomStop);
        estimate.diskSpace = diskSpaceEstimate(estimate.layerName, estimate.tilesTotal, estimate.format);

        if(estimate.timeSpent == 0 || estimate.tilesDone == 0) {
            int tilesPerRequest = getTilesPerRequest(estimate.layerName);
            
            // we only check for throttling when there isn't any real history
            if(estimate.maxThroughput > 0) {
                // even if throttling is more than 5 a second, we'll assume 
                // the user knows the backend can handle what it's throttled to
                estimate.timeRemaining = this.totalTimeEstimate(1, estimate.maxThroughput * tilesPerRequest, estimate.tilesTotal, estimate.threadCount);    
            } else {
                // assumptions will be applied - 5 tiles a second
                estimate.timeRemaining = this.totalTimeEstimate(1, 5 * tilesPerRequest, estimate.tilesTotal, estimate.threadCount);
            }
        } else {
            estimate.timeRemaining = this.totalTimeEstimate(estimate.timeSpent, estimate.tilesDone, estimate.tilesTotal, estimate.threadCount);
        }
        
    }

    private int getTilesPerRequest(String layerName) throws GeoWebCacheException {
        if(seeder == null) {
            throw new GeoWebCacheException("Seeder not available (probably shouldn't have called tileCount with a layerName)");
        }

        TileLayer tl = seeder.findTileLayer(layerName);
        
        int factors[] = tl.getMetaTilingFactors();
        
        if(factors.length == 2) {
            return(factors[0] * factors[1]);
        } else {
            return 1;
        }
    }

    public void setSeeder(TileBreeder seeder) {
        this.seeder = seeder;
    }

}
