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

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.io.Resource;

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
    
    public StorageBroker(MetaStore metaStore, BlobStore blobStore) {
        this.metaStore = metaStore;
        this.blobStore = blobStore;
        
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
        if(metaStoreEnabled) {
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
        if(metaStoreEnabled) {
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
        if(metaStoreEnabled) {
            expired = metaStore.expire(trObj);
        }
        return expired;
    }
    
    
    public boolean get(TileObject tileObj) throws StorageException {
        if(! metaStoreEnabled) {
            boolean found = getBlobOnly(tileObj);
            return found;
        }
        
        if(! metaStore.get(tileObj)) {
            return false;
        }
        
        if(tileObj.getJobId() == -1) {
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
        if(! metaStoreEnabled) {
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
    
    /** 
     * Destroy method for Spring
     */
    public void destroy() {
        log.info("Destroying StorageBroker");
    }

}
