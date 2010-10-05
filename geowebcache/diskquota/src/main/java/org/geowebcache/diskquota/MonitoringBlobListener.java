package org.geowebcache.diskquota;

import static org.geowebcache.diskquota.StorageUnit.B;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.BlobStoreListener;

class MonitoringBlobListener implements BlobStoreListener {

    private static final Log log = LogFactory.getLog(MonitoringBlobListener.class);

    private final DiskQuotaConfig quotaConfig;

    public MonitoringBlobListener(final DiskQuotaConfig quotaConfig) {
        this.quotaConfig = quotaConfig;
    }

    /**
     * @see org.geowebcache.storage.BlobStoreListener#tileStored(java.lang.String, java.lang.String,
     *      java.lang.String, java.lang.String, long, long, int, long)
     */
    public void tileStored(final String layerName, final String gridSetId, final String blobFormat,
            final String parameters, final long x, final long y, final int z, final long blobSize) {

        final LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
        if (layerQuota == null) {
            // there's no quota defined for the layer
            return;
        }
        final int blockSize = quotaConfig.getDiskBlockSize();

        long actuallyUsedStorage = blockSize * (int) Math.ceil((double) blobSize / blockSize);

        Quota globalUsedQuota = quotaConfig.getGlobalUsedQuota();
        Quota usedQuota = layerQuota.getUsedQuota();

        globalUsedQuota.add(actuallyUsedStorage, B);
        usedQuota.add(actuallyUsedStorage, B);

        // inform the layer policy the tile has been added, in case it needs that information
        ExpirationPolicy policy = layerQuota.getExpirationPolicy();
        if(policy != null){
        policy.createInfoFor(layerQuota, gridSetId, x, y, z);
        }

        // mark the config as dirty so its saved when appropriate
        quotaConfig.setDirty(true);
        layerQuota.setDirty(true);
        if (log.isDebugEnabled()) {
            log.trace("Used quota increased for " + layerName + ": " + usedQuota);
        }
    }

    /**
     * @see org.geowebcache.storage.BlobStoreListener#tileDeleted(java.lang.String,
     *      java.lang.String, java.lang.String, java.lang.String, long, long, int, long)
     */
    public void tileDeleted(final String layerName, final String gridSetId,
            final String blobFormat, final String parameters, final long x, final long y,
            final int z, final long blobSize) {

        final LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
        if (layerQuota == null) {
            // there's no quota defined for the layer
            return;
        }
        int blockSize = quotaConfig.getDiskBlockSize();

        long actualTileSizeOnDisk = blockSize * (int) Math.ceil((double) blobSize / blockSize);

        Quota globalUsedQuota = quotaConfig.getGlobalUsedQuota();
        Quota usedQuota = layerQuota.getUsedQuota();

        globalUsedQuota.subtract(actualTileSizeOnDisk, B);
        usedQuota.subtract(actualTileSizeOnDisk, B);

        // inform the layer policy the tile has been deleted, in case it needs that information
        ExpirationPolicy policy = layerQuota.getExpirationPolicy();
        policy.removeInfoFor(layerQuota, gridSetId, x, y, z);

        // mark the config as dirty so its saved when appropriate
        quotaConfig.setDirty(true);
        layerQuota.setDirty(true);
        if (log.isTraceEnabled()) {
            log.trace("Used quota decreased for " + layerName + ": " + usedQuota);
        }
    }

    /**
     * @see org.geowebcache.storage.BlobStoreListener#layerDeleted(java.lang.String)
     */
    public void layerDeleted(final String layerName) {
        final LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
        if (layerQuota == null) {
            // there's no quota defined for the layer
            return;
        }
        ExpirationPolicy expirationPolicy = layerQuota.getExpirationPolicy();
        expirationPolicy.dettach(layerName);
        quotaConfig.remove(layerQuota);
        // mark the config as dirty so its saved when appropriate
        quotaConfig.setDirty(true);
    }
}