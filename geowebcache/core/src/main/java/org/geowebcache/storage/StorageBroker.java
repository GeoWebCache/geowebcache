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

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.io.Resource;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.GWCTaskStatus;

/**
 * Handles cacheable objects (tiles, wfs responses) both in terms of data storage and metadata
 * storage, delegating to a {@link MetaStore} and a {@link BlobStore}
 */
public class StorageBroker {
    private static Log log = LogFactory.getLog(org.geowebcache.storage.StorageBroker.class);

    private BlobStore blobStore;

    private MetaStore metaStore; 

    private boolean metaStoreEnabled = true;

    private boolean verifyFileSize = false;

    private boolean isReady = false;

    private TileLayerDispatcher tileLayerDispatcher;

    public StorageBroker(MetaStore metaStore, BlobStore blobStore, TileLayerDispatcher tileLayerDispatcher) {
        this.metaStore = metaStore;
        this.blobStore = blobStore;
        this.tileLayerDispatcher = tileLayerDispatcher; 

        if(metaStore != null) {
            metaStoreEnabled = metaStore.enabled();
        } else {
            metaStoreEnabled = false;
        }
    }

    public void addBlobStoreListener(BlobStoreListener listener){
        blobStore.addListener(listener);
    }

    public boolean removeBlobStoreListener(BlobStoreListener listener){
        return blobStore.removeListener(listener);
    }

    public void setVerifyFileSize(boolean verifyFileSize) {
        this.verifyFileSize = verifyFileSize;
    }

    public boolean delete(String layerName) throws StorageException {
        boolean ret = true;
        TileLayer layer = null;
        try {
            layer = tileLayerDispatcher.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new StorageException(e.getMessage());
        }
        if(metaStoreEnabled && layer.useMetaStore()) {
            ret = metaStore.delete(layerName);
        }
        ret = (ret && blobStore.delete(layerName));
        return ret;
    }

    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        boolean ret = true;
        if(metaStoreEnabled) {
            ret = metaStore.rename(oldLayerName, newLayerName);
        }
        ret = (ret && blobStore.rename(oldLayerName, newLayerName));
        return ret;
    }

    public boolean delete(TileRange trObj) throws StorageException {
        boolean deleted;
        TileLayer layer = null;
        try {
            layer = tileLayerDispatcher.getTileLayer(trObj.layerName);
        } catch (GeoWebCacheException e) {
            throw new StorageException(e.getMessage());
        }
        if(metaStoreEnabled && layer.useMetaStore()) {
            deleted = metaStore.delete(blobStore, trObj);
        } else {
            if(trObj instanceof DiscontinuousTileRange) {
                throw new StorageException(
                        "DiscontinuousTileRange currently requries a metastore."
                );
            }
            deleted = blobStore.delete(trObj);
        }
        return deleted;
    }

    public boolean expire(TileRange trObj) throws StorageException {
        boolean expired = false;
        TileLayer layer = null;
        try {
            layer = tileLayerDispatcher.getTileLayer(trObj.layerName);
        } catch (GeoWebCacheException e) {
            throw new StorageException(e.getMessage());
        }
        if(metaStoreEnabled && layer.useMetaStore()) {
            expired = metaStore.expire(trObj);
        }
        return expired;
    }


    public boolean get(TileObject tileObj) throws StorageException {
        TileLayer layer = null;
        try {
            layer = tileLayerDispatcher.getTileLayer(tileObj.getLayerName());
        } catch (GeoWebCacheException e) {
            throw new StorageException(e.getMessage());
        }
        if(!metaStoreEnabled || !layer.useMetaStore()) {
            boolean found = getBlobOnly(tileObj);
            return found;
        }

        if(! metaStore.get(tileObj)) {
            log.debug("tile not found in metastore: " + tileObj.toString());
            return false;
        }

        if(tileObj.getId() == -1) {
            throw new StorageException(
            "metaStore.get() returned true, but did not set an id on the object");
        }

        if(tileObj.blob_size > 0) {
            Resource blob = blobStore.get(tileObj);
            if(blob == null) {
                throw new StorageException(
                        "Blob for "+Arrays.toString(tileObj.xyz)+" was expected to have size " 
                        + tileObj.blob_size + " but was null.");
            } else if(verifyFileSize && blob.getSize() != tileObj.blob_size) {
                throw new StorageException(
                        "Blob was expected to have size " 
                        + tileObj.blob_size + " but was " + blob.getSize());
            }

            tileObj.blob = blob;
            return true;
        }
        return false;
    }

    private boolean getBlobOnly(TileObject tileObj) throws StorageException {
        if(tileObj.getParameters() == null 
                || tileObj.getParameters().size() == 0) {
            Resource blob = blobStore.get(tileObj);
            if(blob == null) {
                return false;
            } else {
                tileObj.blob = blob;
                return true;
            }
        } else {
            log.error("Cannot fetch tile with parameters if metastore is disabled!");
            return false;
        }
    }

    public boolean put(TileObject tileObj) throws StorageException {
        TileLayer layer = null;
        try {
            layer = tileLayerDispatcher.getTileLayer(tileObj.getLayerName());
        } catch (GeoWebCacheException e) {}
        if(! metaStoreEnabled || !layer.useMetaStore()) {
            boolean stored = putBlobOnly(tileObj);
            return stored;
        }

        try {
            //System.out.println("Pre metastore put: " + Arrays.toString(tileObj.xyz));
            metaStore.put(tileObj);
            //System.out.println("Pre blobstore put: " + Arrays.toString(tileObj.xyz));
            blobStore.put(tileObj);
            //System.out.println("Pre unlock put: " + Arrays.toString(tileObj.xyz));
            metaStore.unlock(tileObj);

            return true;

        } catch (StorageException se) {
            log.error(se.getMessage());
        }

        return false;
    }

    private boolean putBlobOnly(TileObject tileObj) {
        if(tileObj.getParameters() == null 
                || tileObj.getParameters().size() == 0) {
            try {
                blobStore.put(tileObj);
            } catch (StorageException se) {
                log.error("Unable to save tile: " + se.getMessage());
                return false;
            }
            return true;
        } else {
            log.debug("Cannot save tile with parameters if metastore is disabled!");
            return false;
        }
    }


    public boolean put(GWCTask tskObj) {
        if(! metaStoreEnabled) {
            log.info("Cannot use GWCTask objects if metastore is disabled!");
            return false;
        }

        try {
            metaStore.put(tskObj);
            return true;

        } catch (StorageException se) {
            log.error(se.getMessage());
        }

        return false;
    }


    public void updateGWCTask(GWCTask tskObj) {
        if(! metaStoreEnabled) {
            log.info("Cannot use GWCTask objects if metastore is disabled!");
            return ;
        }

        try {
            metaStore.updateGWCTask(tskObj);
            return ;

        } catch (StorageException se) {
            log.error(se.getMessage());
        }

        return ;
    }

    public void getTasks(String taskIds, ArrayList<GWCTaskStatus> tasks) {
        if(!metaStoreEnabled) {
            log.info("Cannot use GWCTask objects if metastore is disabled!");
            return ;
        }

        try {
            metaStore.getTasks(taskIds, tasks);
            return ;

        } catch (StorageException se) {
            log.error(se.getMessage());
        }

        return ;
    }

    /** 
     * Destroy method for Spring
     */
    public void destroy() {
        log.info("Destroying StorageBroker");
    }

}
