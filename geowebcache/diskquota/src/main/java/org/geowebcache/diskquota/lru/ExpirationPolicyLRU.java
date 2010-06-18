package org.geowebcache.diskquota.lru;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

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

    private TileLayerListener statsCollector;

    public ExpirationPolicyLRU() {
        // this.statsCollector = new LRUStatsCollector(new TilePageCalculator());
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

        Hashtable<String, GridSubset> gridSubsets = tileLayer.getGridSubsets();
        Map<String, TilePageCalculator> pageSizesPerGridSubset = new HashMap<String, TilePageCalculator>();

        for (GridSubset gs : gridSubsets.values()) {
            TilePageCalculator calc = new TilePageCalculator();
            int[][] pageSizes = calc.getPageSizes(gs);
            logLevels(tileLayer, pageSizes);
            pageSizesPerGridSubset.put(gs.getName(), calc);
        }
        tileLayer.addLayerListener(this.statsCollector);
    }

    private void logLevels(TileLayer tileLayer, int[][] pageSizes) {
        if (log.isInfoEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (int level = 0; level < pageSizes.length; level++) {
                sb.append("level ").append(level).append(": ").append(
                        Arrays.toString(pageSizes[level])).append(", ");
            }
            log.info("PageSizes for '" + tileLayer.getName() + "': " + sb.toString());
        }
    }

    /**
     * Per layer LRU statistics collector
     * 
     * @author groldan
     * 
     */
    private static class LRUStatsCollector implements TileLayerListener {

        private final TileLayer layer;

        private final TilePageCalculator pageCalculator;

        /**
         * 
         * @param pageCalculator
         */
        public LRUStatsCollector(final TileLayer layer, final TilePageCalculator pageCalculator) {
            this.layer = layer;
            this.pageCalculator = pageCalculator;
        }

        /**
         * @see org.geowebcache.layer.TileLayerListener#tileRequested(org.geowebcache.layer.TileLayer,
         *      org.geowebcache.conveyor.ConveyorTile)
         */
        public void tileRequested(TileLayer layer, ConveyorTile tile) {
            long[] tileXYZ = tile.getTileIndex();
            String gridSetId = tile.getGridSetId();
            // TODO: discriminate by parameters Id? format?
            long[] pageForTile = pageCalculator.pageFor(tileXYZ, gridSetId);
            if (log.isTraceEnabled()) {
                log.trace("Tile requested: " + Arrays.toString(tile.getTileIndex()) + " page: "
                        + Arrays.asList(pageForTile));
            }
        }

        public void tileSeeded(TileLayer layer, ConveyorTile tile) {
            System.out.println("Tile seeded: " + Arrays.toString(tile.getTileIndex()));
        }

    }

}
