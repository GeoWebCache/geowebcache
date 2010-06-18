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
import java.io.FileFilter;
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
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.WFSObject;
import org.geowebcache.util.FileUtils;
import org.geowebcache.util.ServletUtils;
import org.springframework.util.StopWatch;

/**
 * See BlobStore interface description for details
 * 
 */
public class FileBlobStore implements BlobStore {
    private static Log log = LogFactory.getLog(org.geowebcache.storage.blobstore.file.FileBlobStore.class);
    
    public static final int BUFFER_SIZE = 32768;
    
    private final String path;
    
    private final BlobStoreListenerList listeners = new BlobStoreListenerList();
    
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

        File layerPath = getLayerPath(layerName);

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
        
        listeners.sendLayerDeleted(layerName);
        
        log.info("Truncated " + count + " tiles from " + layerPath);
        return true;
    }

    private File getLayerPath(String layerName) {
        String prefix = path + File.separator 
            + FilePathGenerator.filteredLayerName(layerName);

        File layerPath = new File(prefix);
        return layerPath;
    }
    
    public boolean delete(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, false);
        if (!fh.exists())
            return false;

        //System.out.println("Deleting " + fh.getAbsolutePath());
        
        final long length = fh.length();
        if (!fh.delete()) {
            throw new StorageException("Unable to delete "
                    + fh.getAbsolutePath());
        }
        long[] xyz = stObj.getXYZ();
        listeners.sendTileDeleted(stObj.getLayerName(), stObj.getGridSetId(),
                stObj.getBlobFormat(), stObj.getParameters(), xyz[0], xyz[1], (int) xyz[2], length);
       
        File parentDir = fh.getParentFile();
        
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
    
    public boolean delete(TileRange trObj) throws StorageException {
        int count = 0;

        String prefix = path + File.separator 
            + FilePathGenerator.filteredLayerName(trObj.layerName);

        File layerPath = new File(prefix);

        if (!layerPath.exists() || !layerPath.canWrite()) {
            throw new StorageException(prefix
                    + " does not exist or is not writable.");
        }
        FilePathFilter fpf = new FilePathFilter(trObj);

        final String layerName = trObj.layerName;
        final String gridSetId = trObj.gridSetId;
        final String blobFormat = trObj.mimeType.getFormat();
        final String parameters = trObj.parameters;

        File[] srsZoomDirs = layerPath.listFiles(fpf);

        for (File srsZoom : srsZoomDirs) {
            int zoomLevel = FilePathFilter.findZoomLevel(srsZoom.getName());
            File[] intermediates = srsZoom.listFiles(fpf);

            for (File imd : intermediates) {
                File[] tiles = imd.listFiles(fpf);
                long length;

                for (File tile : tiles) {
                    length = tile.length();
                    boolean deleted = tile.delete();
                    if (deleted) {
                        String[] coords = tile.getName().split("\\.")[0].split("_");
                        long x = Long.parseLong(coords[0]);
                        long y = Long.parseLong(coords[1]);
                        listeners.sendTileDeleted(layerName, gridSetId, blobFormat, parameters, x,
                                y, zoomLevel, length);
                        count++;
                    }
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
        listeners.sendTileStored(stObj);
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

    /**
     * @see org.geowebcache.storage.BlobStore#calculateCacheSize(java.lang.String)
     */
    public double calculateCacheSize(String layerName, int blockSize) throws StorageException {
        File layerPath = getLayerPath(layerName);

        if (!layerPath.exists()) {
            log.debug("No cache directory for layer " + layerName
                    + " yet. Returning zero size for calculateCacheSize");
            return 0D;
        }

        if (!layerPath.canRead()) {
            throw new StorageException(layerPath.getAbsolutePath() + " can't be read.");
        }

        StopWatch sw = new StopWatch();
        sw.start();
        LayerCacheSizeCalculator visitor = new LayerCacheSizeCalculator(layerName, blockSize);
        FileUtils.traverseDepth(layerPath, visitor);
        sw.stop();

        long numTiles = visitor.getNumTiles();
        double aggregateSizeMB = visitor.getAggregateSizeMB();
        log.info("Calculated cache size for layer " + layerName + " in " + sw.getTotalTimeSeconds()
                + " seconds. Cache size: " + aggregateSizeMB + "MB. Num tiles: " + numTiles);
        return aggregateSizeMB;
    }

    private static class LayerCacheSizeCalculator implements FileFilter {

        private static final double MB = 1024 * 1024;

        private double aggregateSizeMB;

        private long count;

        private String layerName;

        private int blockSize;

        public LayerCacheSizeCalculator(final String layerName, int blockSize) {
            this.layerName = layerName;
            this.blockSize = blockSize;
        }

        public boolean accept(final File pathname) {
            if (pathname.isDirectory()) {
                return true;
            }

            // no problem is the file does not exist because it was deleted on our back, length()
            // returns zero.
            double length = blockSize * (1 + pathname.length() / blockSize);
            
            aggregateSizeMB += length / MB;

            count++;

            if (log.isDebugEnabled()) {
                if (count % 1000 == 0) {
                    log.debug("Count " + count + " tile sizes for layer " + layerName
                            + " so far for a total of " + aggregateSizeMB + "MB");
                }
            }
            return false;
        }

        public long getNumTiles() {
            return count;
        }

        public double getAggregateSizeMB() {
            return aggregateSizeMB;
        }
    }

    public void addListener(BlobStoreListener listener) {
        listeners.addListener(listener);
    }

    public boolean removeListener(BlobStoreListener listener) {
        return listeners.removeListener(listener);
    }
}
