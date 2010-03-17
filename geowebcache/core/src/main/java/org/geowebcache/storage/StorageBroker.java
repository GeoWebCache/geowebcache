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
    
    public void setVerifyFileSize(boolean verifyFileSize) {
        this.verifyFileSize = verifyFileSize;
    }
    
    public boolean delete(String layerName) throws StorageException {
        boolean ret = true;
        if(metaStoreEnabled) {
            ret = metaStore.delete(layerName);
        }
        return (ret && blobStore.delete(layerName));
    }
    
    public boolean delete(TileRange trObj) throws StorageException {
        if(metaStoreEnabled) {
            return metaStore.delete(blobStore, trObj);
        } else {
            if(trObj instanceof DiscontinuousTileRange) {
                throw new StorageException(
                        "DiscontinuousTileRange currently requries a metastore."
                        );
            }
            return blobStore.delete(trObj);
        }
    }
    
    public boolean expire(TileRange trObj) throws StorageException {
        if(metaStoreEnabled) {
            return metaStore.expire(trObj);
        }
        return false;
    }
    
    
    public boolean get(TileObject tileObj) throws StorageException {
        if(! metaStoreEnabled) {
            return getBlobOnly(tileObj);
        }
        
        if(! metaStore.get(tileObj)) {
            return false;
        }
        
        if(tileObj.getId() == -1) {
            throw new StorageException(
                    "metaStore.get() returned true, but did not set an id on the object");
        }
        
        if(tileObj.blob_size > 0) {
            byte[] blob = blobStore.get(tileObj);
            if(blob == null) {
                throw new StorageException(
                        "Blob for "+Arrays.toString(tileObj.xyz)+" was expected to have size " 
                        + tileObj.blob_size + " but was null.");
            } else if(verifyFileSize && blob.length != tileObj.blob_size) {
                throw new StorageException(
                        "Blob was expected to have size " 
                        + tileObj.blob_size + " but was " + blob.length);
            }
                
            tileObj.blob = blob;
        }
        
        return true;
    }
    
    private boolean getBlobOnly(TileObject tileObj) throws StorageException {
        if(tileObj.getParameters() == null 
                || tileObj.getParameters().length() == 0) {
            byte[] blob = blobStore.get(tileObj);
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

    public boolean get(WFSObject wfsObj) throws StorageException {
        if (!metaStore.get(wfsObj)) {
            log.error("Cannot use WFS objects if metastore is disabled!");
            return false;
        }

        if (wfsObj.getId() == -1) {
            throw new StorageException(
                    "metaStore.get() returned true, but did not set an id on the object");
        }

        if(blobStore.get(wfsObj) == 0) {
            throw new StorageException("The blob for WFS " + Long.toString(wfsObj.getId()) + " was of size 0");
        }
        
        return true;
    }
    
    public boolean put(TileObject tileObj) {
        if(! metaStoreEnabled) {
            return putBlobOnly(tileObj);
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
                || tileObj.getParameters().length() == 0) {
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

    public boolean put(WFSObject wfsObj) {
        if(! metaStoreEnabled) {
            log.debug("Cannot use WFS objects if metastore is disabled!");
            return false;
        }
        
        try {
            metaStore.put(wfsObj);
            blobStore.put(wfsObj);
            metaStore.unlock(wfsObj);
            return true;
            
        } catch (StorageException se) {
            log.error(se.getMessage());
        }

        return false;
    }
    
    /** 
     * Destroy method for Spring
     */
    public void destroy() {
        log.info("Destroying StorageBroker");
    }
}
