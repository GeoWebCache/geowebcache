package org.geowebcache.diskquota;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.TileLayerListener;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.Assert;

public class UsageStatsMonitor {

    private static final Log log = LogFactory.getLog(UsageStatsMonitor.class);

    private static final CustomizableThreadFactory tf = new CustomizableThreadFactory(
            "GWC DiskQuota Usage Stats Gathering Thread-");

    private final QuotaStore quotaStore;

    private final TileLayerDispatcher tileLayerDispatcher;

    private final TilePageCalculator tilePageCalculator;

    /**
     * Single threaded executor service for the {@link #usageStatsConsumer}
     */
    private ExecutorService executorService;

    /**
     * Queue shared by the stats producer and the consumer
     */
    private BlockingQueue<UsageStats> sharedQueue;

    /**
     * Listens to all {@link TileLayer layers}
     * {@link TileLayerListener#tileRequested(TileLayer, org.geowebcache.conveyor.ConveyorTile)
     * tileRequested} events and puts usage statistics on the {@link #sharedQueue} for the consumer
     * to save them to the {@link #quotaStore}
     */
    private QueuedUsageStatsProducer usageStatsProducer;

    /**
     * Task that constantly polls the {@link #sharedQueue} for usage statistics payload objects and
     * aggregates them to be saved to the {@link #quotaStore} for the LRU and LFU
     * {@link ExpirationPolicy expiration policies}
     */
    private QueuedUsageStatsConsumer usageStatsConsumer;

    public UsageStatsMonitor(final QuotaStore quotaStore,
            final TileLayerDispatcher tileLayerDispatcher) {

        Assert.notNull(quotaStore, "quotaStore is null");
        Assert.notNull(tileLayerDispatcher, "tileLayerDispatcher is null");

        this.quotaStore = quotaStore;
        this.tileLayerDispatcher = tileLayerDispatcher;
        this.tilePageCalculator = quotaStore.getTilePageCalculator();
    }

    public void startUp() {
        executorService = Executors.newSingleThreadExecutor(tf);

        sharedQueue = new LinkedBlockingQueue<UsageStats>(1000);

        usageStatsConsumer = new QueuedUsageStatsConsumer(quotaStore, sharedQueue,
                tilePageCalculator);
        executorService.submit(usageStatsConsumer);

        usageStatsProducer = new QueuedUsageStatsProducer(sharedQueue);
        Iterable<TileLayer> allLayers = tileLayerDispatcher.getLayerList();
        for (TileLayer layer : allLayers) {
            layer.addLayerListener(usageStatsProducer);
        }
    }

    /**
     * Calls for a shut down and waits until any remaining task finishes before returning
     */
    public void shutDown() {
        final boolean cancel = false;
        shutDown(cancel);

        final int maxAttempts = 6;
        final int seconds = 5;
        int attempts = 1;
        while (!executorService.isTerminated()) {
            attempts++;
            try {
                awaitTermination(seconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                String message = "Usage statistics thread helper for DiskQuota failed to shutdown within "
                        + (attempts * seconds)
                        + " seconds. Attempt "
                        + attempts
                        + " of "
                        + maxAttempts + "...";
                log.warn(message);
                if (attempts == maxAttempts) {
                    throw new RuntimeException(message, e);
                }
            }
        }
    }

    public void awaitTermination(int timeout, TimeUnit units) throws InterruptedException {
        if (!executorService.isShutdown()) {
            throw new IllegalStateException("Called awaitTermination but the "
                    + "UsageStatsMonitor is not shutting down");
        }
        executorService.awaitTermination(timeout, units);
    }

    /**
     * Calls for a shut down and returns immediatelly
     */
    public void shutDownNow() {
        shutDown(true);
    }

    private void shutDown(final boolean cancel) {
        Iterable<TileLayer> allLayers = tileLayerDispatcher.getLayerList();
        for (TileLayer layer : allLayers) {
            try {
                layer.removeLayerListener(usageStatsProducer);
            } catch (RuntimeException e) {
                log.error("Unexpected exception while removing the usage stats "
                        + "listener from layer '" + layer
                        + "'. Ignoring in order to continue with the monitor's shutdown "
                        + "process", e);
            }
        }

        usageStatsConsumer.shutdown();
        if (cancel) {
            usageStatsProducer.setCancelled(true);
            executorService.shutdownNow();
        } else {
            executorService.shutdown();
        }
        sharedQueue = null;
    }

}
