package org.geowebcache.diskquota;

import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerListener;

public class ExpirationPolicyLRU implements LayerQuotaExpirationPolicy {

    private static final String POLICY_NAME = "LRU";

    private TileLayerListener statsCollector;

    public ExpirationPolicyLRU() {
        this.statsCollector = new StatsCollector();
    }

    public String getName() {
        return POLICY_NAME;
    }

    public void attach(TileLayer tileLayer, Quota quota) {
        tileLayer.addLayerListener(this.statsCollector);
    }

    private static class StatsCollector implements TileLayerListener {

        public void tileRequested(TileLayer layer, ConveyorTile tile) {
            // System.err.println(Arrays.toString(tile.getTileIndex()));
        }

        public void tileSeeded(TileLayer layer, ConveyorTile tile) {
            // System.out.println(Arrays.toString(tile.getTileIndex()));
        }

    }

}
