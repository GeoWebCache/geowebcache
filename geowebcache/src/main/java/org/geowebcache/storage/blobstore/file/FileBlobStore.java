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
package org.geowebcache.storage.blobstore.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.StorageObject;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.WFSObject;
import org.geowebcache.util.ServletUtils;

/**
 * See BlobStore interface description for details
 * 
 */
public class FileBlobStore implements BlobStore {
    public static final int BUFFER_SIZE = 32768;
    
    private final String path;
    
    public FileBlobStore(String rootPath) throws StorageException {
        path = rootPath;
        File fh = new File(path);
        
        if(! fh.exists() || ! fh.isDirectory() || !  fh.canWrite()) {
            throw new StorageException(path + " is not writable a writable directory.");
        }
    }

    public boolean delete(StorageObject stObj) throws StorageException {
        if (stObj instanceof TileObject) {
            return delete((TileObject) stObj);
        } else if (stObj instanceof WFSObject) {
            return delete((WFSObject) stObj);
        } else {
            throw new StorageException(
                    "Cannot handle objects of type "
                    + stObj.getClass());
        }   
    }
    
    private boolean delete(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, false);
        if (!fh.exists())
            return false;

        if (!fh.delete()) {
            throw new StorageException("Unable to delete "
                    + fh.getAbsolutePath());
        }

        return true;
    }

    private boolean delete(WFSObject stObj) throws StorageException {
        if(stObj.getQueryBlobSize() != -1) {
            File fh = getFileHandleWFS(stObj, true, false);

            if (fh.exists() && !fh.delete()) {
                throw new StorageException("Unable to delete "
                        + fh.getAbsolutePath());
            }
        }
        
        File fh = getFileHandleWFS(stObj, false, false);
        if (!fh.exists())
            return false;

        if (!fh.delete()) {
            throw new StorageException("Unable to delete "
                    + fh.getAbsolutePath());
        }

        return true;
    }


    public byte[] get(StorageObject stObj) throws StorageException {
        byte[] res = null;
        if (stObj instanceof TileObject) {
            res = get((TileObject) stObj);
        } else if (stObj instanceof WFSObject) {
            res = get((WFSObject) stObj);
        } else {
            throw new StorageException(
                    "Cannot handle objects of type "
                    + stObj.getClass());
        }
        
        stObj.setBlob(res);
        
        return res;
    }

    private byte[] get(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, false);
        return readFile(fh);
    }

    private byte[] get(WFSObject stObj) throws StorageException {
        // Should we check and compare the blobs?
        File fh = getFileHandleWFS(stObj, false, false);
        return readFile(fh);
    }

    public void put(StorageObject stObj) throws StorageException {
        if (stObj.getBlobSize() == -1)
            return;

        if (stObj instanceof TileObject) {
            put((TileObject) stObj);
        } else if (stObj instanceof WFSObject) {
            put((WFSObject) stObj);
        } else {
            throw new StorageException("Cannot handle objects of type "
                    + stObj.getClass());
        }
    }
    
    private void put(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, true);
        writeFile(fh,stObj.getBlob());
    }
    
    private void put(WFSObject stObj) throws StorageException {
        if(stObj.getQueryBlobSize() != -1) {
            File queryfh = getFileHandleWFS(stObj,true, true);
            writeFile(queryfh, stObj.getQueryBlob());
        }
        
        File responsefh = getFileHandleWFS(stObj, false, true);
        writeFile(responsefh,stObj.getBlob());
    }
    
    private File getFileHandleTile(TileObject stObj, boolean create) {
        String parentPath = path +File.separator+ stObj.getType();
        if(create) {
            File parent = new File(parentPath);
            parent.mkdirs();
        }
        
        return new File(parentPath + File.separator + stObj.getId());
    }
    
    private File getFileHandleWFS(WFSObject stObj, boolean query, boolean create) {
        String parentPath;
        
        if(query) {
            parentPath = path +File.separator+ "wfs" + File.separator + "query";
        } else {
            parentPath = path +File.separator+ "wfs" + File.separator + "response";
        }
        
        if(create) {
            File dir = new File(parentPath);
            dir.mkdirs();
        }
        
        return new File(parentPath + File.separator + stObj.getId());
    }
    
    
    private byte[] readFile(File fh) throws StorageException {
        byte[] blob = null;

        FileInputStream fis;
        try {
            fis = new FileInputStream(fh);
        } catch (FileNotFoundException e) {
            return null;
        }

        try {
            blob = ServletUtils.readStream(fis, BUFFER_SIZE, BUFFER_SIZE);
        } catch (IOException ioe) {
            throw new StorageException(ioe.getMessage() + " for "
                    + fh.getAbsolutePath());
        } finally {
            try {
                fis.close();
            } catch (IOException ioe) {
                throw new StorageException(ioe.getMessage() + " for "
                        + fh.getAbsolutePath());
            }
        }
        
        return blob;
    }
    
    private void writeFile(File fh, byte[] blob) throws StorageException {
        // Open the output stream
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(fh);
        } catch (FileNotFoundException ioe) {
            throw new StorageException(ioe.getMessage() + " for "
                    + fh.getAbsolutePath());
        }

        // Write the stream
        try {
            fos.write(blob);
        } catch (IOException ioe) {
            throw new StorageException(ioe.getMessage() + " for "
                    + fh.getAbsolutePath());
        } finally {
            try {
                fos.close();
            } catch (IOException ioe) {
                throw new StorageException(ioe.getMessage() + " for "
                        + fh.getAbsolutePath());
            }
        }
    }
    
    public void clear() throws StorageException {
        throw new StorageException("Not implemented yet!");
    }
}
