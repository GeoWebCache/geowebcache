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
 * storage, delegating most work to a {@link BlobStore}
 */
public class DefaultStorageBroker implements StorageBroker {
    private static Log log = LogFactory.getLog(org.geowebcache.storage.DefaultStorageBroker.class);

    private BlobStore blobStore;

    private TransientCache transientCache;
    
    public DefaultStorageBroker(BlobStore blobStore) {
        this.blobStore = blobStore;

        // @todo are these settings reasonable? should they be configurable?
        transientCache = new TransientCache(100,1000);
    }

    public void addBlobStoreListener(BlobStoreListener listener){
        blobStore.addListener(listener);
    }
    
    public boolean removeBlobStoreListener(BlobStoreListener listener){
        return blobStore.removeListener(listener);
    }
    
    public boolean delete(String layerName) throws StorageException {
        return blobStore.delete(layerName);
    }

    public boolean deleteByGridSetId(final String layerName, final String gridSetId)
            throws StorageException {
        return blobStore.deleteByGridsetId(layerName, gridSetId);
    }

    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        return blobStore.rename(oldLayerName, newLayerName);
    }

    public boolean delete(TileRange trObj) throws StorageException {
        return blobStore.delete(trObj);
    }

    public boolean get(TileObject tileObj) throws StorageException {
        return blobStore.get(tileObj);
    }

    public boolean put(TileObject tileObj) throws StorageException {
        blobStore.put(tileObj);
        return true;
    }

    public void destroy() {
        log.info("Destroying StorageBroker");
    }

    public String getLayerMetadata(final String layerName, final String key) {
        return this.blobStore.getLayerMetadata(layerName, key);
    }

    public void putLayerMetadata(final String layerName, final String key, final String value) {
        this.blobStore.putLayerMetadata(layerName, key, value);
    }

    public boolean getTransient(TileObject tile) {
        String key = TransientCache.computeTransientKey(tile);
        Resource resource;
        synchronized (transientCache) {
            resource = transientCache.get(key);
        }
        tile.setBlob(resource); 
        return resource != null;
    }

    public void putTransient(TileObject tile) {
        String key = TransientCache.computeTransientKey(tile);
        synchronized (transientCache) {
            transientCache.put(key, tile.getBlob());
        }
    }
}
