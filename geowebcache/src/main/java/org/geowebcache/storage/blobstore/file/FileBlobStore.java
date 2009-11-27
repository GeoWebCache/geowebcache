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
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRangeObject;
import org.geowebcache.storage.WFSObject;
import org.geowebcache.util.ServletUtils;

/**
 * See BlobStore interface description for details
 * 
 */
public class FileBlobStore implements BlobStore {
    private static Log log = LogFactory.getLog(org.geowebcache.storage.blobstore.file.FileBlobStore.class);
    
    public static final int BUFFER_SIZE = 32768;
    
    private final String path;
    
    
    public FileBlobStore(DefaultStorageFinder defStoreFinder) throws StorageException {
        path = defStoreFinder.getDefaultPath();
    }
    
    public FileBlobStore(String rootPath) throws StorageException {
        path = rootPath;
        File fh = new File(path);
        
        if(! fh.exists() || ! fh.isDirectory() || !  fh.canWrite()) {
            throw new StorageException(path + " is not writable directory.");
        }
    }
    
    public boolean delete(String layerName) throws StorageException {
        int count = 0;

        String prefix = path + File.separator 
            + FilePathGenerator.filteredLayerName(layerName);

        File layerPath = new File(prefix);

        if (!layerPath.exists() || !layerPath.canWrite()) {
            log.info(layerPath + " does not exist or is not writable");
            return false;
        }
        File[] srsZoomDirs = layerPath.listFiles();

        for (File srsZoom : srsZoomDirs) {
            File[] intermediates = srsZoom.listFiles();

            for (File imd : intermediates) {
                File[] tiles = imd.listFiles();

                for (File tile : tiles) {
                    tile.delete();
                    count++;
                }

                String[] chk = imd.list();
                if (chk == null || chk.length == 0) {
                    imd.delete();
                    count++;
                }
            }

            String[] chk = srsZoom.list();
            if (chk == null || chk.length == 0) {
                srsZoom.delete();
                count++;
            }

        }

        if(layerPath.exists()) {
            layerPath.delete();
        }
        
        
        log.info("Truncated " + count + " tiles from " + layerPath);
        return true;
    }
    
    public boolean delete(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, false);
        if (!fh.exists())
            return false;

        //System.out.println("Deleting " + fh.getAbsolutePath());
        
        if (!fh.delete()) {
            throw new StorageException("Unable to delete "
                    + fh.getAbsolutePath());
        }
        
        File parentDir = new File(fh.getParent());
        
        // TODO This could potentially be very slow
        if(parentDir.isDirectory() && parentDir.canWrite() && parentDir.list().length == 0) {
            parentDir.delete();
        }

        return true;
    }

    
    public boolean delete(WFSObject stObj) throws StorageException {
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
    
    public boolean delete(TileRangeObject trObj) throws StorageException {
        int count = 0;

        String prefix = path + File.separator 
            + FilePathGenerator.filteredLayerName(trObj.layerName);

        File layerPath = new File(prefix);

        if (!layerPath.exists() || !layerPath.canWrite()) {
            throw new StorageException(prefix
                    + " does not exist or is not writable.");
        }
        FilePathFilter fpf = new FilePathFilter(trObj);

        File[] srsZoomDirs = layerPath.listFiles(fpf);

        for (File srsZoom : srsZoomDirs) {
            File[] intermediates = srsZoom.listFiles(fpf);

            for (File imd : intermediates) {
                File[] tiles = imd.listFiles(fpf);

                for (File tile : tiles) {
                    tile.delete();
                    count++;
                }

                String[] chk = imd.list();
                if (chk == null || chk.length == 0) {
                    imd.delete();
                    count++;
                }
            }

            String[] chk = srsZoom.list();
            if (chk == null || chk.length == 0) {
                srsZoom.delete();
                count++;
            }

        }

        log.info("Truncated " + count + " tiles");

        return true;
    }

    public byte[] get(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, false);
        return readFile(fh);
    }

    public long get(WFSObject stObj) throws StorageException {
        // Should we check and compare the blobs?
        File fh = getFileHandleWFS(stObj, false, false);
        //return readFile(fh);
        
        stObj.setInputStream(getFileInputStream(fh));
        
        return fh.length();
    }
    
    public void put(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, true);
        writeFile(fh,stObj.getBlob());
    }
    
    public void put(WFSObject stObj) throws StorageException {
        if(stObj.getQueryBlobSize() != -1) {
            File queryfh = getFileHandleWFS(stObj,true, true);
            writeFile(queryfh, stObj.getQueryBlob());
        }
        
        File responsefh = getFileHandleWFS(stObj, false, true);
        writeFile(responsefh,stObj.getInputStream());
        
        InputStream is = getFileInputStream(responsefh);

        stObj.setInputStream(is);
    }
    
    private File getFileHandleTile(TileObject stObj, boolean create) {
        String[] paths = null;
        try {
            paths = FilePathGenerator.tilePath(
                    path, stObj.getLayerName(),
                    stObj.getXYZ(), stObj.getGridSetId(), 
                    MimeType.createFromFormat(stObj.getBlobFormat()), 
                    stObj.getParametersId());
        } catch (MimeException me) {
            log.error(me.getMessage());
        }

        if (create) {
            File parent = new File(paths[0]);
            parent.mkdirs();
        }

        return new File(paths[0] + File.separator + paths[1]);
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
    
    private InputStream getFileInputStream(File fh) throws StorageException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fh);
        } catch (FileNotFoundException e) {
            return null;
        }
        
        return fis;
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
    
    private int writeFile(File fh, InputStream is) throws StorageException {
        // Open the output stream
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(fh);
        } catch (FileNotFoundException ioe) {
            throw new StorageException(ioe.getMessage() + " for "
                    + fh.getAbsolutePath());
        }

        byte[] buffer = new byte[2048];
        int read = 0;
        int total = 0;
        try {
            while(read != -1) {
                 read = is.read(buffer);
                 if(read != -1) {
                     fos.write(buffer, 0, read);
                     total += read;
                 }
            }
        } catch (IOException ioe) {
            throw new StorageException(ioe.getMessage() + " for "
                    + fh.getAbsolutePath());
        } finally {
            try {
                fos.close();
                is.close();
            } catch (IOException ioe) {
                throw new StorageException(ioe.getMessage() + " for "
                        + fh.getAbsolutePath());
            }
        }
        return read;
    }
       
    public void clear() throws StorageException {
        throw new StorageException("Not implemented yet!");
    }

    /** 
     * Destroy method for Spring
     */
    public void destroy() {
       // Do nothing 
    }

//    public boolean isReady() {
//        File fh = new File(path + File.separator + "version");
//        if(fh.isFile() && fh.canRead()) {
//            try {
//                FileInputStream fis = new FileInputStream(fh);
//                byte[] ret = ServletUtils.readStream(fis, 10, 10);
//                
//                int ver = Integer.parseInt(new String(ret));
//                
//                if(ver == 120) {
//                    return true;
//                }
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            return true;
//        }
//        log.error("Could not find " + fh.getAbsolutePath() + ", upgrade required.");
//        return false;
//    }
}
