/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp / The Open Planning Project 2009
 */
package org.geowebcache.storage;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;

/**
 * Handles cacheable objects (tiles, wfs responses) both in terms of data storage and metadata
 * storage, delegating most work to a {@link BlobStore}
 */
public class DefaultStorageBroker implements StorageBroker {
    private static Logger log = Logging.getLogger(DefaultStorageBroker.class.getName());

    private BlobStore blobStore;

    private TransientCache transientCache;

    public DefaultStorageBroker(BlobStore blobStore, TransientCache transientCache) {
        this.blobStore = blobStore;
        this.transientCache = transientCache;
    }

    @Override
    public void addBlobStoreListener(BlobStoreListener listener) {
        blobStore.addListener(listener);
    }

    @Override
    public boolean removeBlobStoreListener(BlobStoreListener listener) {
        return blobStore.removeListener(listener);
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        return blobStore.delete(layerName);
    }

    @Override
    public boolean deleteByGridSetId(final String layerName, final String gridSetId)
            throws StorageException {
        return blobStore.deleteByGridsetId(layerName, gridSetId);
    }

    @Override
    public boolean deleteByParameters(final String layerName, final Map<String, String> parameters)
            throws StorageException {
        return blobStore.deleteByParameters(layerName, parameters);
    }

    @Override
    public boolean deleteByParametersId(final String layerName, String parametersId)
            throws StorageException {
        return blobStore.deleteByParametersId(layerName, parametersId);
    }

    @Override
    public boolean purgeOrphans(final TileLayer layer) throws StorageException {
        return blobStore.purgeOrphans(layer);
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        return blobStore.rename(oldLayerName, newLayerName);
    }

    @Override
    public boolean delete(TileRange trObj) throws StorageException {
        return blobStore.delete(trObj);
    }

    @Override
    public boolean get(TileObject tileObj) throws StorageException {
        return blobStore.get(tileObj);
    }

    @Override
    public boolean put(TileObject tileObj) throws StorageException {
        blobStore.put(tileObj);
        return true;
    }

    @Override
    public void destroy() {
        log.fine("Destroying StorageBroker");
    }

    @Override
    public String getLayerMetadata(final String layerName, final String key) {
        return this.blobStore.getLayerMetadata(layerName, key);
    }

    @Override
    public void putLayerMetadata(final String layerName, final String key, final String value) {
        this.blobStore.putLayerMetadata(layerName, key, value);
    }

    @Override
    public boolean getTransient(TileObject tile) {
        String key = TransientCache.computeTransientKey(tile);
        Resource resource;
        synchronized (transientCache) {
            resource = transientCache.get(key);
        }
        tile.setBlob(resource);
        return resource != null;
    }

    @Override
    public void putTransient(TileObject tile) {
        String key = TransientCache.computeTransientKey(tile);
        synchronized (transientCache) {
            transientCache.put(key, tile.getBlob());
        }
    }

    /**
     * Method for accessing directly the blobstore used by the following StorageBroker
     *
     * @return the {@link BlobStore} object used
     */
    public BlobStore getBlobStore() {
        return blobStore;
    }

    @Override
    public Set<String> getCachedParameterIds(String layerName) throws StorageException {
        return this.blobStore.getParameterIds(layerName);
    }

    @Override
    public Set<Map<String, String>> getCachedParameters(String layerName) throws StorageException {
        return this.blobStore.getParameters(layerName);
    }
}
