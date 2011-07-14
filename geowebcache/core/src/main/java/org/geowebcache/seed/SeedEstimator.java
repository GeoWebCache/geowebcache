package org.geowebcache.seed;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;

public class SeedEstimator {

    TileBreeder seeder = null;
    
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
            long[] gridBounds = coveredGridLevels[i];

            long thisLevel = (1 + gridBounds[2] - gridBounds[0])
                    * (1 + gridBounds[3] - gridBounds[1]);

            if (thisLevel > (Long.MAX_VALUE / 4) && i != stopZoom) {
                return -1;
            } else {
                count += thisLevel;
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
            gridSetId = tl.getGridSubsets().entrySet().iterator().next().getKey();
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
        long result = Math.round((double) timeSpent * (((double) tilesTotal / threadCount) / (double) tilesDone));
//        if(maxThroughput > 0) {
//            long throttledResult = Math.round((double) 1 * (((double) tilesTotal / threadCount) / (double) maxThroughput / 9)); // assuming 9 tiles per request = bad
//            return throttledResult;
//        } else {
            return result;
//        }
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

        if(estimate.timeSpent == 0 || estimate.tilesDone == 0) {
            int tilesPerRequest = getTilesPerRequest(estimate.layerName);
            
            // we only check for throttling when there isn't any real history
            if(estimate.maxThroughput > 0) {
                // assumptions will be applied
                estimate.timeRemaining = this.totalTimeEstimate(1, estimate.maxThroughput * tilesPerRequest, estimate.tilesTotal, estimate.threadCount);    
            } else {
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
