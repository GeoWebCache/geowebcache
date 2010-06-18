package org.geowebcache.diskquota.lru;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.diskquota.LayerQuotaExpirationPolicy;
import org.geowebcache.diskquota.Quota;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerListener;

/**
 * Singleton bean that expects {@link Quota}s to be {@link #attach(TileLayer, Quota) attached} for
 * all monitored {@link TileLayer layers} and whips out pages of tiles when a layer exceeds its
 * quota, based on a Least Recently Used algorithm.
 * 
 * @author groldan
 * 
 */
public class ExpirationPolicyLRU implements LayerQuotaExpirationPolicy {

    private static final Log log = LogFactory.getLog(ExpirationPolicyLRU.class);

    private static final String POLICY_NAME = "LRU";

    public ExpirationPolicyLRU() {

    }

    /**
     * @see org.geowebcache.diskquota.LayerQuotaExpirationPolicy#getName()
     */
    public String getName() {
        return POLICY_NAME;
    }

    /**
     * @see org.geowebcache.diskquota.LayerQuotaExpirationPolicy#attach(org.geowebcache.layer.TileLayer,
     *      org.geowebcache.diskquota.Quota)
     */
    public void attach(final TileLayer tileLayer, final Quota quota) {
        log.info("Attaching layer '" + tileLayer.getName() + "' to cache expiration policy "
                + getName());
        
        TilePageCalculator calc = new TilePageCalculator(tileLayer.getGridSubsets());

        logLevels(tileLayer, calc);

        TileLayerListener statsCollector = new LRUStatsCollector(calc);
        tileLayer.addLayerListener(statsCollector);
    }

    private void logLevels(TileLayer tileLayer, TilePageCalculator calc) {
        if (log.isInfoEnabled()) {
            for (GridSubset gs : tileLayer.getGridSubsets().values()) {
                int[][] pageSizes = calc.getPageSizes(gs.getName());
                StringBuilder sb = new StringBuilder();
                for (int level = 0; level < pageSizes.length; level++) {
                    sb.append("level ").append(level).append(": ").append(
                            Arrays.toString(pageSizes[level])).append(", ");
                }
                log.info("PageSizes for '" + tileLayer.getName() + "': " + sb.toString());
            }
        }
    }

    public void dettach(String layerName) {
    }

    public void recordTile(String layerName, String gridSetId, String blobFormat,
            String parameters, long x, long y, int z, long blobSize) {
    }

    public void removeTile(String layerName, String gridSetId, String blobFormat,
            String parameters, long x, long y, int z, long blobSize) {
    }

    /**
     * Per layer LRU statistics collector
     * 
     * @author groldan
     * 
     */
    private static class LRUStatsCollector implements TileLayerListener {

        private final TilePageCalculator pageCalculator;

        /**
         * 
         * @param pageCalculator
         */
        public LRUStatsCollector(final TilePageCalculator pageCalculator) {
            this.pageCalculator = pageCalculator;
        }

        /**
         * @see org.geowebcache.layer.TileLayerListener#tileRequested(org.geowebcache.layer.TileLayer,
         *      org.geowebcache.conveyor.ConveyorTile)
         */
        public void tileRequested(TileLayer layer, ConveyorTile tile) {
            long[] tileXYZ = tile.getTileIndex();
            String gridSetId = tile.getGridSetId();
//            String parameters = tile.getParameters();
//            String storageFormat = tile.getMimeType().getFormat();
            // TODO: discriminate by parameters Id? format?
            TilePage page = pageCalculator.pageFor(tileXYZ, gridSetId);
            page.markHit();
            if (log.isTraceEnabled()) {
                log.trace("Tile requested: " + Arrays.toString(tile.getTileIndex()) + " page: "
                        + page);
            }
        }

    }

}
