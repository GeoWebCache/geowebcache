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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.DefaultStorageBroker;
import org.springframework.util.Assert;

/**
 * Monitors {@link DefaultStorageBroker} activity to keep track of the disk usage.
 *
 * <p>This class only cares about receiving {@link BlobStoreListener} events and submitting {@link QuotaUpdate}s to the
 * provided {@link BlockingQueue}. Another thread is responsible of taking the {@link QuotaUpdate} off the queue and
 * updating the quota store as appropriate.
 *
 * @author groldan
 * @see DiskQuotaMonitor
 * @see QueuedQuotaUpdatesConsumer
 */
class QueuedQuotaUpdatesProducer implements BlobStoreListener {

    private static final Logger log = Logging.getLogger(QueuedQuotaUpdatesProducer.class.getName());

    private final BlockingQueue<QuotaUpdate> queuedUpdates;

    private boolean cancelled;

    private final QuotaStore quotaStore;

    int updateOfferTimeoutSeconds;

    /**
     * @param queuedUpdates queue that this monitor will fill with updates at each tile event. There should be a
     *     separate thread that takes care of them.
     */
    public QueuedQuotaUpdatesProducer(final BlockingQueue<QuotaUpdate> queuedUpdates, QuotaStore quotaStore) {
        Assert.notNull(queuedUpdates, "queuedUpdates can't be null");

        this.queuedUpdates = queuedUpdates;
        this.quotaStore = quotaStore;

        String timeoutStr = GeoWebCacheExtensions.getProperty("GEOWEBCACHE_QUOTA_DIFF_TIMEOUT");
        this.updateOfferTimeoutSeconds = 5 * 60; // by default five minutes
        if (timeoutStr != null) {
            updateOfferTimeoutSeconds = Integer.parseInt(timeoutStr);
        }
    }

    /**
     * Receives notification of a tile stored and updates the corresponding layer quota info.
     *
     * @see org.geowebcache.storage.BlobStoreListener#tileStored
     */
    @Override
    public void tileStored(
            final String layerName,
            final String gridSetId,
            final String blobFormat,
            final String parametersId,
            final long x,
            final long y,
            final int z,
            final long blobSize) {
        if (blobSize == 0) {
            return;
        }

        quotaUpdate(layerName, gridSetId, blobFormat, parametersId, blobSize, new long[] {x, y, z});
    }

    /** @see org.geowebcache.storage.BlobStoreListener#tileDeleted */
    @Override
    public void tileDeleted(
            final String layerName,
            final String gridSetId,
            final String blobFormat,
            final String parametersId,
            final long x,
            final long y,
            final int z,
            final long blobSize) {

        long actualSizeFreed = -1 * blobSize;

        quotaUpdate(layerName, gridSetId, blobFormat, parametersId, actualSizeFreed, new long[] {x, y, z});
    }

    /** @see org.geowebcache.storage.BlobStoreListener#tileUpdated */
    @Override
    public void tileUpdated(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize,
            long oldSize) {

        long delta = blobSize - oldSize;

        if (delta == 0) {
            return;
        }

        long[] tileIndex = {x, y, z};
        quotaUpdate(layerName, gridSetId, blobFormat, parametersId, delta, tileIndex);
    }

    /**
     * @see org.geowebcache.storage.BlobStoreListener#layerDeleted(java.lang.String)
     * @see QuotaStore#deleteLayer(String)
     */
    @Override
    public void layerDeleted(final String layerName) {
        quotaStore.deleteLayer(layerName);
    }

    @Override
    public void gridSubsetDeleted(String layerName, String gridSetId) {
        quotaStore.deleteGridSubset(layerName, gridSetId);
    }

    @Override
    public void parametersDeleted(String layerName, String parametersId) {
        quotaStore.deleteParameters(layerName, parametersId);
    }

    @Override
    public void layerRenamed(String oldLayerName, String newLayerName) {
        try {
            quotaStore.renameLayer(oldLayerName, newLayerName);
        } catch (InterruptedException e) {
            log.log(Level.SEVERE, "Can't rename " + oldLayerName + " to " + newLayerName + " in quota store", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Defers executing the update of the quota usage for the given tile set by adding a {@link QuotaUpdate} payload to
     * {@link #queuedUpdates} so that the consumer thread performs the update without blocking the calling thread.
     *
     * @param amount positive to signal a quota increase, negative to signal a quota decrease
     * @param tileIndex tile index
     */
    private void quotaUpdate(
            String layerName, String gridSetId, String blobFormat, String parametersId, long amount, long[] tileIndex) {

        if (cancelled(layerName)) {
            return;
        }
        QuotaUpdate payload = new QuotaUpdate(layerName, gridSetId, blobFormat, parametersId, amount, tileIndex);
        try {
            if (updateOfferTimeoutSeconds <= 0) {
                this.queuedUpdates.put(payload);
            } else {
                if (!this.queuedUpdates.offer(payload, updateOfferTimeoutSeconds, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to offer the quota diff to the updates queue "
                            + "within the configured timeout of "
                            + updateOfferTimeoutSeconds
                            + " seconds");
                }
            }
        } catch (InterruptedException e) {
            if (cancelled(layerName)) {
                return;
            }
            log.info("Quota updates on "
                    + layerName
                    + " abruptly interrupted on thread "
                    + Thread.currentThread().getName()
                    + ".");
            Thread.currentThread().interrupt();
        }
    }

    private boolean cancelled(String layerName) {
        if (cancelled) {
            log.fine("Quota updates listener cancelled. Avoiding adding update for layer "
                    + layerName
                    + " to quota information queue");
        }
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
