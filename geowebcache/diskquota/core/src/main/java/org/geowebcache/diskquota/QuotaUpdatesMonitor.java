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
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.storage.StorageBroker;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.Assert;

public class QuotaUpdatesMonitor extends AbstractMonitor {

    private static final Logger log = Logging.getLogger(QuotaUpdatesMonitor.class.getName());
    private static final CustomizableThreadFactory tf =
            new CustomizableThreadFactory("GWC DiskQuota Updates Gathering Thread-");

    private final StorageBroker storageBroker;

    private final QuotaStore quotaStore;

    private BlockingQueue<QuotaUpdate> sharedQueue;

    private QueuedQuotaUpdatesProducer quotaDiffsProducer;

    private QueuedQuotaUpdatesConsumer quotaUsageUpdatesConsumer;

    public QuotaUpdatesMonitor(final StorageBroker storageBroker, final QuotaStore quotaStore) {
        Assert.notNull(storageBroker, "storageBroker is null");
        Assert.notNull(quotaStore, "quotaStore is null");

        this.storageBroker = storageBroker;
        this.quotaStore = quotaStore;

        String sizeStr = GeoWebCacheExtensions.getProperty("GEOWEBCACHE_QUOTA_QUEUE_SIZE");
        int quotaQueueSize = 1000;
        if (sizeStr != null) {
            quotaQueueSize = Integer.parseInt(sizeStr);
        }
        if (quotaQueueSize > 0) {
            this.sharedQueue = new LinkedBlockingQueue<>(quotaQueueSize);
        } else {
            this.sharedQueue = new LinkedBlockingQueue<>();
        }
    }

    @Override
    public void startUp() {
        super.startUp();

        quotaDiffsProducer = new QueuedQuotaUpdatesProducer(sharedQueue, quotaStore);

        // the task that takes quota updates from the queue and saves them to the store
        quotaUsageUpdatesConsumer = new QueuedQuotaUpdatesConsumer(quotaStore, sharedQueue);

        // the listener that puts quota updates on the queue
        storageBroker.addBlobStoreListener(quotaDiffsProducer);

        getExecutorService().submit(quotaUsageUpdatesConsumer);
    }

    @Override
    protected void shutDown(final boolean cancel) {
        log.fine("Shutting down quota usage monitor...");
        try {
            storageBroker.removeBlobStoreListener(quotaDiffsProducer);
        } catch (RuntimeException e) {
            log.log(
                    Level.SEVERE,
                    "Unexpected exception while removing the disk quota monitor listener from the StorageBroker."
                            + " Ignoring in order to continue with the monitor's shutdown "
                            + "process",
                    e);
        }

        if (cancel) {
            quotaDiffsProducer.setCancelled(true);
            getExecutorService().shutdownNow();
        } else {
            getExecutorService().shutdown();
        }
        sharedQueue = null;
    }

    @Override
    public void shutDown() {
        quotaUsageUpdatesConsumer.shutdown();
        super.shutDown();
    }

    public void tileStored(
            final String layerName,
            final String gridSetId,
            final String blobFormat,
            final String parametersId,
            final long x,
            final long y,
            final int z,
            final long blobSize) {
        this.quotaDiffsProducer.tileStored(layerName, gridSetId, blobFormat, parametersId, x, y, z, blobSize);
    }

    @Override
    protected CustomizableThreadFactory getThreadFactory() {
        return tf;
    }
}
