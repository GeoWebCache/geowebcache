/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.diskquota;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.TileLayerListener;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.Assert;

public class UsageStatsMonitor extends AbstractMonitor {

    private static final Logger log = Logging.getLogger(UsageStatsMonitor.class.getName());

    private static final CustomizableThreadFactory tf =
            new CustomizableThreadFactory("GWC DiskQuota Usage Stats Gathering Thread-");

    private final QuotaStore quotaStore;

    private final TileLayerDispatcher tileLayerDispatcher;

    private final TilePageCalculator tilePageCalculator;

    /** Queue shared by the stats producer and the consumer */
    private BlockingQueue<UsageStats> sharedQueue;

    /**
     * Listens to all {@link TileLayer layers} {@link TileLayerListener#tileRequested(TileLayer,
     * org.geowebcache.conveyor.ConveyorTile) tileRequested} events and puts usage statistics on the
     * {@link #sharedQueue} for the consumer to save them to the {@link #quotaStore}
     */
    private QueuedUsageStatsProducer usageStatsProducer;

    /**
     * Task that constantly polls the {@link #sharedQueue} for usage statistics payload objects and aggregates them to
     * be saved to the {@link #quotaStore} for the LRU and LFU {@link ExpirationPolicy expiration policies}
     */
    private QueuedUsageStatsConsumer usageStatsConsumer;

    public UsageStatsMonitor(final QuotaStore quotaStore, final TileLayerDispatcher tileLayerDispatcher) {

        Assert.notNull(quotaStore, "quotaStore is null");
        Assert.notNull(tileLayerDispatcher, "tileLayerDispatcher is null");

        this.quotaStore = quotaStore;
        this.tileLayerDispatcher = tileLayerDispatcher;
        this.tilePageCalculator = quotaStore.getTilePageCalculator();
    }

    @Override
    public void startUp() {
        super.startUp();

        sharedQueue = new LinkedBlockingQueue<>(1000);

        usageStatsConsumer = new QueuedUsageStatsConsumer(quotaStore, sharedQueue, tilePageCalculator);
        getExecutorService().submit(usageStatsConsumer);

        usageStatsProducer = new QueuedUsageStatsProducer(sharedQueue);
        Iterable<TileLayer> allLayers = tileLayerDispatcher.getLayerList();
        for (TileLayer layer : allLayers) {
            layer.addLayerListener(usageStatsProducer);
        }
    }

    @Override
    protected void shutDown(final boolean cancel) {
        Iterable<TileLayer> allLayers = tileLayerDispatcher.getLayerList();
        for (TileLayer layer : allLayers) {
            try {
                layer.removeLayerListener(usageStatsProducer);
            } catch (RuntimeException e) {
                log.log(
                        Level.SEVERE,
                        "Unexpected exception while removing the usage stats "
                                + "listener from layer '"
                                + layer
                                + "'. Ignoring in order to continue with the monitor's shutdown "
                                + "process",
                        e);
            }
        }

        usageStatsConsumer.shutdown();
        if (cancel) {
            usageStatsProducer.setCancelled(true);
            getExecutorService().shutdownNow();
        } else {
            getExecutorService().shutdown();
        }
        sharedQueue = null;
    }

    @Override
    protected CustomizableThreadFactory getThreadFactory() {
        return tf;
    }
}
