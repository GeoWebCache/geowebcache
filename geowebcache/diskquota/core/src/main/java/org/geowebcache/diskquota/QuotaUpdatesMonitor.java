package org.geowebcache.diskquota;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.StorageBroker;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.Assert;

public class QuotaUpdatesMonitor {

    private static final Log log = LogFactory.getLog(QuotaUpdatesMonitor.class);

    private static final CustomizableThreadFactory tf = new CustomizableThreadFactory(
            "GWC DiskQuota Updates Gathering Thread-");

    private final DiskQuotaConfig quotaConfig;

    private final StorageBroker storageBroker;

    private final QuotaStore quotaStore;

    private ExecutorService executorService;

    private BlockingQueue<QuotaUpdate> sharedQueue;

    private QueuedQuotaUpdatesProducer quotaDiffsProducer;

    private QueuedQuotaUpdatesConsumer quotaUsageUpdatesConsumer;

    public QuotaUpdatesMonitor(final DiskQuotaConfig quotaConfig,
            final StorageBroker storageBroker, final QuotaStore quotaStore) {
        Assert.notNull(quotaConfig, "quotaConfig is null");
        Assert.notNull(storageBroker, "storageBroker is null");
        Assert.notNull(quotaStore, "quotaStore is null");

        this.quotaConfig = quotaConfig;
        this.storageBroker = storageBroker;
        this.quotaStore = quotaStore;

    }

    public void startUp() {
        executorService = Executors.newSingleThreadExecutor(tf);

        sharedQueue = new LinkedBlockingQueue<QuotaUpdate>(1000);// set a capacity?
        quotaDiffsProducer = new QueuedQuotaUpdatesProducer(quotaConfig, sharedQueue, quotaStore);

        // the task that takes quota updates from the queue and saves them to the store
        quotaUsageUpdatesConsumer = new QueuedQuotaUpdatesConsumer(quotaStore, sharedQueue);

        // the listener that puts quota updates on the queue
        storageBroker.addBlobStoreListener(quotaDiffsProducer);

        executorService.submit(quotaUsageUpdatesConsumer);
    }

    private void shutDown(final boolean cancel) {
        log.info("Shutting down quota usage monitor...");
        try {
            storageBroker.removeBlobStoreListener(quotaDiffsProducer);
        } catch (RuntimeException e) {
            log.error(
                    "Unexpected exception while removing the disk quota monitor listener from the StorageBroker."
                            + " Ignoring in order to continue with the monitor's shutdown "
                            + "process", e);
        }

        if (cancel) {
            quotaDiffsProducer.setCancelled(true);
            executorService.shutdownNow();
        } else {
            executorService.shutdown();
        }
        sharedQueue = null;
    }

    /**
     * Calls for a shut down and returns immediatelly
     */
    public void shutDownNow() {
        shutDown(true);
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

    public void tileStored(final String layerName, final String gridSetId, final String blobFormat,
            final String parametersId, final long x, final long y, final int z, final long blobSize) {
        this.quotaDiffsProducer.tileStored(layerName, gridSetId, blobFormat, parametersId, x, y, z,
                blobSize);
    }

    public void awaitTermination(int timeout, TimeUnit units) throws InterruptedException {
        if (!executorService.isShutdown()) {
            throw new IllegalStateException("Called awaitTermination but the "
                    + "UsageStatsMonitor is not shutting down");
        }
        executorService.awaitTermination(10 * 1000, TimeUnit.MILLISECONDS);
    }

}
