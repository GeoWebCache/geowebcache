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
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota;

import java.util.concurrent.BlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.DefaultStorageBroker;
import org.springframework.util.Assert;

/**
 * Monitors {@link DefaultStorageBroker} activity to keep track of the disk usage.
 * <p>
 * This class only cares about receiving {@link BlobStoreListener} events and submitting
 * {@link QuotaUpdate}s to the provided {@link BlockingQueue}. Another thread is responsible of
 * taking the {@link QuotaUpdate} off the queue and updating the quota store as appropriate.
 * </p>
 * 
 * @author groldan
 * @see DiskQuotaMonitor
 * @see QueuedQuotaUpdatesConsumer
 */
class QueuedQuotaUpdatesProducer implements BlobStoreListener {

    private static final Log log = LogFactory.getLog(QueuedQuotaUpdatesProducer.class);

    private final DiskQuotaConfig quotaConfig;

    private final BlockingQueue<QuotaUpdate> queuedUpdates;

    private boolean cancelled;

    private final QuotaStore quotaStore;

    /**
     * 
     * @param quotaConfig
     *            needed to get the {@link DiskQuotaConfig#getDiskBlockSize() disk block size} at
     *            each tile event, so that the computation is consistent with config changes at
     *            runtime
     * @param queuedUpdates
     *            queue that this monitor will fill with updates at each tile event. There should be
     *            a separate thread that takes care of them.
     */
    public QueuedQuotaUpdatesProducer(final DiskQuotaConfig quotaConfig,
            final BlockingQueue<QuotaUpdate> queuedUpdates, QuotaStore quotaStore) {
        Assert.notNull(quotaConfig, "quotaConfig can't be null");
        Assert.notNull(queuedUpdates, "queuedUpdates can't be null");

        this.quotaConfig = quotaConfig;
        this.queuedUpdates = queuedUpdates;
        this.quotaStore = quotaStore;
    }

    /**
     * Receives notification of a tile stored and updates the corresponding layer quota info.
     * 
     * @see org.geowebcache.storage.BlobStoreListener#tileStored
     */
    public void tileStored(final String layerName, final String gridSetId, final String blobFormat,
            final String parametersId, final long x, final long y, final int z, final long blobSize) {
        if (blobSize == 0) {
            return;
        }
        final int blockSize = quotaConfig.getDiskBlockSize();

        long actuallyUsedStorage = blockSize * (int) Math.ceil((double) blobSize / blockSize);

        quotaUpdate(layerName, gridSetId, blobFormat, parametersId, actuallyUsedStorage,
                new long[] { x, y, z });
    }

    /**
     * @see org.geowebcache.storage.BlobStoreListener#tileDeleted
     */
    public void tileDeleted(final String layerName, final String gridSetId,
            final String blobFormat, final String parametersId, final long x, final long y,
            final int z, final long blobSize) {

        int blockSize = quotaConfig.getDiskBlockSize();

        long actualSizeFreed = -1 * (blockSize * (int) Math.ceil((double) blobSize / blockSize));

        quotaUpdate(layerName, gridSetId, blobFormat, parametersId, actualSizeFreed, new long[] {
                x, y, z });
    }

    /**
     * 
     * @see org.geowebcache.storage.BlobStoreListener#tileUpdated
     */
    public void tileUpdated(String layerName, String gridSetId, String blobFormat,
            String parametersId, long x, long y, int z, long blobSize, long oldSize) {

        int blockSize = quotaConfig.getDiskBlockSize();
        double delta = blobSize - oldSize;
        long actualDifference = blockSize * (int) Math.ceil(delta / blockSize);

        if (actualDifference == 0) {
            return;
        }

        long[] tileIndex = new long[] { x, y, z };
        quotaUpdate(layerName, gridSetId, blobFormat, parametersId, actualDifference, tileIndex);
    }

    /**
     * @see org.geowebcache.storage.BlobStoreListener#layerDeleted(java.lang.String)
     * @see QuotaStore#deleteLayer(String)
     */
    public void layerDeleted(final String layerName) {
        quotaStore.deleteLayer(layerName);
    }

    public void gridSubsetDeleted(String layerName, String gridSetId) {
        quotaStore.deleteGridSubset(layerName, gridSetId);
    }

    public void layerRenamed(String oldLayerName, String newLayerName) {
        try {
            quotaStore.renameLayer(oldLayerName, newLayerName);
        } catch (InterruptedException e) {
            log.error("Can't rename " + oldLayerName + " to " + newLayerName + " in quota store", e);
        }
    }

    /**
     * Defers executing the update of the quota usage for the given tile set by adding a
     * {@link QuotaUpdate} payload to {@link #queuedUpdates} so that the consumer thread performs
     * the update without blocking the calling thread.
     * 
     * @param layerName
     * @param gridSetId
     * @param blobFormat
     * @param parametersId
     * @param amount
     *            positive to signal a quota increase, negative to signal a quota decrease
     * @param tileIndex
     *            tile index
     */
    private void quotaUpdate(String layerName, String gridSetId, String blobFormat,
            String parametersId, long amount, long[] tileIndex) {

        if (cancelled(layerName)) {
            return;
        }
        QuotaUpdate payload = new QuotaUpdate(layerName, gridSetId, blobFormat, parametersId,
                amount, tileIndex);
        try {
            this.queuedUpdates.put(payload);
        } catch (InterruptedException e) {
            if (cancelled(layerName)) {
                return;
            }
            log.info("Quota updates on " + layerName + " abruptly interrupted on thread "
                    + Thread.currentThread().getName() + ".");
        }
    }

    private boolean cancelled(String layerName) {
        if (cancelled) {
            log.debug("Quota updates listener cancelled. Avoiding adding update for layer "
                    + layerName + " to quota information queue");
        }
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
 
}