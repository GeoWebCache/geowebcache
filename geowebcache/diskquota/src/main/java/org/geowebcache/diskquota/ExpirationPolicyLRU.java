package org.geowebcache.diskquota;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerListener;

public class ExpirationPolicyLRU implements LayerQuotaExpirationPolicy {

    private static final Log log = LogFactory.getLog(ExpirationPolicyLRU.class);

    private static final String POLICY_NAME = "LRU";

    private TileLayerListener statsCollector;

    public ExpirationPolicyLRU() {
        this.statsCollector = new StatsCollector(new TilePageCalculator());
    }

    public String getName() {
        return POLICY_NAME;
    }

    public void attach(TileLayer tileLayer, Quota quota) {

        TilePageCalculator calc = new TilePageCalculator();

        Hashtable<String, GridSubset> gridSubsets = tileLayer.getGridSubsets();
        Map<String, int[][]> pageSizesPerGridSubset = new HashMap<String, int[][]>();

        for (GridSubset gs : gridSubsets.values()) {
            int[][] pageSizes = calc.getPageSizes(gs);
            logLevels(tileLayer, pageSizes);
            pageSizesPerGridSubset.put(gs.getName(), pageSizes);
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

    private static class StatsCollector implements TileLayerListener {

        private final TilePageCalculator pageCalculator;

        public StatsCollector(TilePageCalculator pageCalculator) {
            this.pageCalculator = pageCalculator;
        }

        public void tileRequested(TileLayer layer, ConveyorTile tile) {
            long[] tileXYZ = tile.getTileIndex();
            GridSubset gridSubset = tile.getGridSubset();
            long[] pageForTile = pageCalculator.pageFor(tileXYZ, gridSubset);
            System.err.println("Tile requested: " + Arrays.toString(tile.getTileIndex())
                    + " page: " + Arrays.asList(pageForTile));
        }

        public void tileSeeded(TileLayer layer, ConveyorTile tile) {
            System.out.println("Tile seeded: " + Arrays.toString(tile.getTileIndex()));
        }

    }

}
