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
        policy.createTileInfo(layerQuota, gridSetId, x, y, z);
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
        policy.removeTileInfo(layerQuota, gridSetId, x, y, z);

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