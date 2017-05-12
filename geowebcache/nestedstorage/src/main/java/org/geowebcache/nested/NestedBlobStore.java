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
 * @author Stuart Adam, Ordnance Survey, Copyright 2017
 */
package org.geowebcache.nested;

import java.util.Map;
import java.util.Optional;

import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

public class NestedBlobStore implements BlobStore {

    private BlobStore frontStore;

    private BlobStore backingStore;

    public NestedBlobStore(NestedBlobStoreConfig config) {
        setFrontStore(config.getFrontStore());
        setBackingStore(config.getBackingStore());
    }

    public void setFrontStore(BlobStore frontStore) {
        this.frontStore = frontStore;
    }

    public void setBackingStore(BlobStore backingStore) {
        this.backingStore = backingStore;
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        boolean ret = false;
        try {
            ret = backingStore.deleteByGridsetId(layerName, gridSetId);
        } finally {
            frontStore.deleteByGridsetId(layerName, gridSetId);
        }
        return ret;
    }
    
    @Override
    public boolean deleteByParametersId(String layerName, String parametersId)
            throws StorageException {
        boolean ret = false;
        try {
            ret = backingStore.deleteByParametersId(layerName, parametersId);
        } finally {
            frontStore.deleteByParametersId(layerName, parametersId);
        }
        return ret;
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        boolean ret = false;
        try {
            ret = backingStore.delete(layerName);
        } finally {
            frontStore.delete(layerName);
        }
        return ret;
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        boolean ret = false;
        try {
            ret = backingStore.delete(obj);
        } finally {
            frontStore.delete(obj);
        }
        return ret;
    }

    @Override
    public boolean delete(TileRange obj) throws StorageException {
        boolean ret = false;
        try {
            ret = backingStore.delete(obj);
        } finally {
            frontStore.delete(obj);
        }
        return ret;
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        boolean answer = frontStore.get(obj);
        if (!answer) {
            answer = backingStore.get(obj);
            if (answer) {
                frontStore.put(obj);
            }
        }
        return answer;
    }

    @Override
    public void put(TileObject obj) throws StorageException {
        backingStore.put(obj);
        frontStore.put(obj);
    }

    @Override
    public void clear() throws StorageException {
        throw new UnsupportedOperationException("clear() should not be called");
    }

    @Override
    public void destroy() {

    }

    @Override
    public void addListener(BlobStoreListener listener) {
        backingStore.addListener(listener);
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return backingStore.removeListener(listener);
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        boolean answer = false;
        try {
            answer = backingStore.rename(oldLayerName, newLayerName);
        } finally {
            frontStore.rename(oldLayerName, newLayerName);
        }
        return answer;
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        String layerMetaData = frontStore.getLayerMetadata(layerName, key);
        if (null == layerMetaData) {
            layerMetaData = backingStore.getLayerMetadata(layerName, key);
            frontStore.putLayerMetadata(layerName, key, layerMetaData);
        }
        return layerMetaData;
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        backingStore.putLayerMetadata(layerName, key, value);
        frontStore.putLayerMetadata(layerName, key, value);
    }

    @Override
    public boolean layerExists(String layerName) {
        return frontStore.layerExists(layerName) || backingStore.layerExists(layerName);
    }

    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        return backingStore.getParametersMapping(layerName);
    }
}
