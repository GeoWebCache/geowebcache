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
 * @author Arne Kepp / The Open Planning Project 2009
 *  
 */
package org.geowebcache.storage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.io.Resource;

/**
 * Handles cacheable objects (tiles, wfs responses) both in terms of data storage and metadata
 * storage, delegating to a {@link MetaStore} and a {@link BlobStore}
 */
public class LegacyStorageBroker implements StorageBroker {
    private static Log log = LogFactory.getLog(org.geowebcache.storage.LegacyStorageBroker.class);

    private BlobStore blobStore;

    private boolean verifyFileSize = false;
    
    private TransientCache transientCache;
    
    public LegacyStorageBroker(BlobStore blobStore) {
        this.blobStore = blobStore;

        // @todo are these settings reasonable? should they be configurable?
        transientCache = new TransientCache(100,1000);
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#addBlobStoreListener(org.geowebcache.storage.BlobStoreListener)
     */
    public void addBlobStoreListener(BlobStoreListener listener){
        blobStore.addListener(listener);
    }
    
    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#removeBlobStoreListener(org.geowebcache.storage.BlobStoreListener)
     */
    public boolean removeBlobStoreListener(BlobStoreListener listener){
        return blobStore.removeListener(listener);
    }
    
    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#setVerifyFileSize(boolean)
     */
    public void setVerifyFileSize(boolean verifyFileSize) {
        this.verifyFileSize = verifyFileSize;
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#delete(java.lang.String)
     */
    public boolean delete(String layerName) throws StorageException {
       return false;
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#deleteByGridSetId(java.lang.String, java.lang.String)
     */
    public boolean deleteByGridSetId(final String layerName, final String gridSetId)
            throws StorageException {
        return false;
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#rename(java.lang.String, java.lang.String)
     */
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        return false;
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#delete(org.geowebcache.storage.TileRange)
     */
    public boolean delete(TileRange trObj) throws StorageException {
        return false;
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#get(org.geowebcache.storage.TileObject)
     */
    public boolean get(TileObject tileObj) throws StorageException {
        return getBlobOnly(tileObj);
    }

    private boolean getBlobOnly(TileObject tileObj) throws StorageException {
        Resource blob = blobStore.get(tileObj);
        if (blob == null) {
            return false;
        } else {
            tileObj.blob = blob;
            return true;
        }
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#put(org.geowebcache.storage.TileObject)
     */
    public boolean put(TileObject tileObj) throws StorageException {
        blobStore.put(tileObj);
        return true;
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#destroy()
     */
    public void destroy() {
        log.info("Destroying StorageBroker");
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#getLayerMetadata(java.lang.String, java.lang.String)
     */
    public String getLayerMetadata(final String layerName, final String key) {
        return this.blobStore.getLayerMetadata(layerName, key);
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#putLayerMetadata(java.lang.String, java.lang.String, java.lang.String)
     */
    public void putLayerMetadata(final String layerName, final String key, final String value) {
        this.blobStore.putLayerMetadata(layerName, key, value);
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#getTransient(org.geowebcache.storage.TileObject)
     */
    public boolean getTransient(TileObject tile) {
        String key = TransientCache.computeTransientKey(tile);
        Resource resource;
        synchronized (transientCache) {
            resource = transientCache.get(key);
        }
        tile.setBlob(resource); 
        return resource != null;
    }

    /* (non-Javadoc)
     * @see org.geowebcache.storage.StorageBroker#putTransient(org.geowebcache.storage.TileObject)
     */
    public void putTransient(TileObject tile) {
        String key = TransientCache.computeTransientKey(tile);
        synchronized (transientCache) {
            transientCache.put(key, tile.getBlob());
        }
    }
}
