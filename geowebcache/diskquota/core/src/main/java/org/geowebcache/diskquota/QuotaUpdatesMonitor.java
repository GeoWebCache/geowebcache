/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */
package org.geowebcache.diskquota;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheExtensions;
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
        
        String sizeStr = GeoWebCacheExtensions.getProperty("GEOWEBCACHE_QUOTA_QUEUE_SIZE");
        int quotaQueueSize = 1000;
        if(sizeStr != null) {
            quotaQueueSize = Integer.parseInt(sizeStr);
        }
        if(quotaQueueSize > 0) {
            this.sharedQueue = new LinkedBlockingQueue<QuotaUpdate>(quotaQueueSize);
        } else {
            this.sharedQueue = new LinkedBlockingQueue<QuotaUpdate>();
        }
    }

    public void startUp() {
        executorService = Executors.newSingleThreadExecutor(tf);

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
        quotaUsageUpdatesConsumer.shutdown();
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
        executorService.awaitTermination(timeout, units);
    }

}
