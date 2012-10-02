package org.geowebcache.diskquota;

import java.util.concurrent.BlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerListener;
import org.springframework.util.Assert;

/**
 * 
 * <p>
 * This {@link TileLayerListener} is thread safe and can be called by any thread requesting a tile
 * from a {@link TileLayer}
 * 
 * @author groldan
 * 
 */
public class QueuedUsageStatsProducer implements TileLayerListener {

    private static final Log log = LogFactory.getLog(QueuedQuotaUpdatesProducer.class);

    private final BlockingQueue<UsageStats> usageStatsQueue;

    private volatile boolean cancelled;

    public QueuedUsageStatsProducer(BlockingQueue<UsageStats> usageStatsQueue) {
        Assert.notNull(usageStatsQueue, "usageStatsQueue can't be null");
        this.usageStatsQueue = usageStatsQueue;
    }

    /**
     * @see org.geowebcache.layer.TileLayerListener#tileRequested
     */
    public void tileRequested(TileLayer layer, ConveyorTile tile) {
        String layerName = layer.getName();
        if (cancelled(layerName)) {
            return;
        }
        String gridsetId = tile.getGridSetId();
        String blobFormat = tile.getMimeType().getFormat();
        String parametersId = tile.getParametersId();
        TileSet tileSet = new TileSet(layerName, gridsetId, blobFormat, parametersId);
        long[] tileIndex = tile.getTileIndex().clone();
        UsageStats usageLog = new UsageStats(tileSet, tileIndex);
        try {
            usageStatsQueue.put(usageLog);
        } catch (InterruptedException e) {
            if (cancelled(layerName)) {
                return;
            }
            log.info("Quota usage stats gathering for " + layerName
                    + " abruptly interrupted on thread " + Thread.currentThread().getName());
        }
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    private boolean cancelled(String layerName) {
        if (cancelled) {
            log.debug("Quota usage stats listener cancelled. Avoiding adding update for layer "
                    + layerName + " to tile page store");
        }
        return cancelled;
    }

}
