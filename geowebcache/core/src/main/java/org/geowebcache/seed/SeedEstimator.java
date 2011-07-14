package org.geowebcache.seed;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;

public class SeedEstimator {

    TileBreeder seeder;
    
    private static SeedEstimator instance = null;
    
    private SeedEstimator() {
        seeder = new TileBreeder();
    }
    
    public static SeedEstimator getInstance() {
        if(instance == null) {
            instance = new SeedEstimator();
        }
        return instance;
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
    
    public long tileCount(String layerName, String gridSetId, BoundingBox bounds, int zoomStart, int zoomStop) throws GeoWebCacheException {
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
        estimate.timeRemaining = this.totalTimeEstimate(estimate.timeSpent, estimate.tilesDone, estimate.tilesTotal, estimate.threadCount);
        
    }
}
