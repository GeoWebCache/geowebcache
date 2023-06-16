/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.diskquota;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerListener;
import org.springframework.util.Assert;

/**
 * This {@link TileLayerListener} is thread safe and can be called by any thread requesting a tile
 * from a {@link TileLayer}
 *
 * @author groldan
 */
public class QueuedUsageStatsProducer implements TileLayerListener {

    private static final Logger log = Logging.getLogger(QueuedQuotaUpdatesProducer.class.getName());

    private final BlockingQueue<UsageStats> usageStatsQueue;

    private volatile boolean cancelled;

    public QueuedUsageStatsProducer(BlockingQueue<UsageStats> usageStatsQueue) {
        Assert.notNull(usageStatsQueue, "usageStatsQueue can't be null");
        this.usageStatsQueue = usageStatsQueue;
    }

    /** @see org.geowebcache.layer.TileLayerListener#tileRequested */
    @Override
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
            log.info(
                    "Quota usage stats gathering for "
                            + layerName
                            + " abruptly interrupted on thread "
                            + Thread.currentThread().getName());
            Thread.currentThread().interrupt();
        }
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    private boolean cancelled(String layerName) {
        if (cancelled) {
            log.fine(
                    "Quota usage stats listener cancelled. Avoiding adding update for layer "
                            + layerName
                            + " to tile page store");
        }
        return cancelled;
    }
}
