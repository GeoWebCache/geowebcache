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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.util.ServletUtils;

public class StorageBroker {
    private static Log log = LogFactory.getLog(org.geowebcache.storage.StorageBroker.class);
    
    private BlobStore blobStore;
    
    private MetaStore metaStore; 
    
    public StorageBroker(MetaStore metaStore, BlobStore blobStore) {
        this.metaStore = metaStore;
        this.blobStore = blobStore;
    }
    
    public void get(TileObject tileObj) throws StorageException {
        metaStore.get(tileObj);
        if(tileObj.getId() == -1) {
            throw new StorageException(
                    "metaStore did not set an id on the object");
        }
        
        if(tileObj.blob_size > 0) {
            byte[] blob = blobStore.get(tileObj);
            if(blob == null) {
                throw new StorageException(
                        "Blob was expected to have size " 
                        + tileObj.blob_size + " but was null.");
            } else if(blob.length != tileObj.blob_size) {
                throw new StorageException(
                        "Blob was expected to have size " 
                        + tileObj.blob_size + " but was " + blob.length);
            }
                
            tileObj.blob = blob;
        }
    }
    
    public void get(WFSObject wfsObj) throws StorageException {
        //if(WFSObject)
    }
    
    public void put(TileObject tileObj) throws StorageException {
        metaStore.put(tileObj);
        blobStore.put(tileObj);
    }
    
    public void put(WFSObject wfsObj) throws StorageException {
        metaStore.put(wfsObj);
        blobStore.put(wfsObj);
    }
}
