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
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.BlobStoreVisitor;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.util.ServletUtils;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * See BlobStore interface description for details
 * 
 */
public class FileBlobStore implements BlobStore {
    private static Log log = LogFactory
            .getLog(org.geowebcache.storage.blobstore.file.FileBlobStore.class);

    public static final int BUFFER_SIZE = 32768;

    private final File stagingArea;

    private final String path;

    private final BlobStoreListenerList listeners = new BlobStoreListenerList();

    private static ExecutorService deleteExecutorService;

    public FileBlobStore(DefaultStorageFinder defStoreFinder) throws StorageException {
        path = defStoreFinder.getDefaultPath();
        stagingArea = new File(path, "_gwc_in_progress_deletes_");
        createDeleteExecutorService();
        issuePendingDeletes();
    }

    public FileBlobStore(String rootPath) throws StorageException {
        path = rootPath;
        File fh = new File(path);

        if (!fh.exists() || !fh.isDirectory() || !fh.canWrite()) {
            throw new StorageException(path + " is not writable directory.");
        }
        stagingArea = new File(path, "_gwc_in_progress_deletes_");
        createDeleteExecutorService();
        issuePendingDeletes();
    }

    private void issuePendingDeletes() {
        if (!stagingArea.exists()) {
            return;
        }
        if (!stagingArea.isDirectory() || !stagingArea.canWrite()) {
            throw new IllegalStateException("Staging area is not writable or is not a directory: "
                    + stagingArea.getAbsolutePath());
        }
        File[] pendings = stagingArea.listFiles();
        for (File directory : pendings) {
            if (directory.isDirectory()) {
                deletePending(directory);
            }
        }
    }

    private void deletePending(final File pendingDeleteDirectory) {
        FileBlobStore.deleteExecutorService.submit(new DefferredDirectoryDeleteTask(
                pendingDeleteDirectory));
    }

    private void createDeleteExecutorService() {
        CustomizableThreadFactory tf;
        tf = new CustomizableThreadFactory("GWC FileStore delete directory thread-");
        tf.setDaemon(true);
        tf.setThreadPriority(Thread.MIN_PRIORITY);
        deleteExecutorService = Executors.newFixedThreadPool(1);
    }

    /**
     * Destroy method for Spring
     */
    public void destroy() {
        deleteExecutorService.shutdownNow();
    }

    private static class DefferredDirectoryDeleteTask implements Runnable {

        private final File directory;

        public DefferredDirectoryDeleteTask(final File directory) {
            this.directory = directory;
        }

        public void run() {
            try {
                deleteDirectory(directory);
            } catch (IOException e) {
                log.warn("Exception occurred while deleting '" + directory.getAbsolutePath() + "'",
                        e);
            } catch (InterruptedException e) {
                log.info("FileStore delete background service interrupted while deleting '"
                        + directory.getAbsolutePath()
                        + "'. Process will be resumed at next start up");
            }
        }

        private void deleteDirectory(File directory) throws IOException, InterruptedException {
            if (!directory.exists()) {
                return;
            }
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            File[] files = directory.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                File file = files[i];
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    if (!file.delete()) {
                        throw new IOException("Unable to delete " + file.getAbsolutePath());
                    }
                }
            }
            if (!directory.delete()) {
                String message = "Unable to delete directory " + directory + ".";
                throw new IOException(message);
            }
        }

    }

    public boolean delete(final String layerName) throws StorageException {
        final File layerPath = getLayerPath(layerName);

        if (!layerPath.exists() || !layerPath.canWrite()) {
            log.info(layerPath + " does not exist or is not writable");
            return false;
        }
        if (!stagingArea.exists() && !stagingArea.mkdirs()) {
            throw new StorageException("Can't create staging directory for deletes: "
                    + stagingArea.getAbsolutePath());
        }
        String dirName = FilePathGenerator.filteredLayerName(layerName);
        File tmpFolder = new File(stagingArea, dirName);
        int tries = 0;
        while (tmpFolder.exists()) {
            ++tries;
            dirName = FilePathGenerator.filteredLayerName(layerName + "." + tries);
            tmpFolder = new File(layerPath.getParentFile(), dirName);
        }
        boolean renamed = layerPath.renameTo(tmpFolder);
        if (!renamed) {
            throw new IllegalStateException("Can't rename " + layerPath.getAbsolutePath() + " to "
                    + tmpFolder.getAbsolutePath() + " for deletion");
        }
        deletePending(tmpFolder);
        this.listeners.sendLayerDeleted(layerName);
        return true;
        // File[] srsZoomDirs = layerPath.listFiles();
        //
        // for (File srsZoom : srsZoomDirs) {
        // File[] intermediates = srsZoom.listFiles();
        //
        // for (File imd : intermediates) {
        // File[] tiles = imd.listFiles();
        //
        // for (File tile : tiles) {
        // tile.delete();
        // count++;
        // }
        //
        // String[] chk = imd.list();
        // if (chk == null || chk.length == 0) {
        // imd.delete();
        // count++;
        // }
        // }
        //
        // String[] chk = srsZoom.list();
        // if (chk == null || chk.length == 0) {
        // srsZoom.delete();
        // count++;
        // }
        //
        // }
        //
        // if (layerPath.exists()) {
        // layerPath.delete();
        // }
        //
        // listeners.sendLayerDeleted(layerName);
        //
        // log.info("Truncated " + count + " tiles from " + layerPath);
        // return true;
    }

    private File getLayerPath(String layerName) {
        String prefix = path + File.separator + FilePathGenerator.filteredLayerName(layerName);

        File layerPath = new File(prefix);
        return layerPath;
    }

    public boolean delete(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, false);
        if (!fh.exists()) {
            return false;
        }

        // System.out.println("Deleting " + fh.getAbsolutePath());

        final long length = fh.length();
        if (!fh.delete()) {
            throw new StorageException("Unable to delete " + fh.getAbsolutePath());
        }
        stObj.setBlobSize((int) length);
        listeners.sendTileDeleted(stObj);

        File parentDir = fh.getParentFile();

        // TODO This could potentially be very slow
        if (parentDir.isDirectory() && parentDir.canWrite() && parentDir.list().length == 0) {
            parentDir.delete();
        }

        return true;
    }

    public boolean delete(TileRange trObj) throws StorageException {
        int count = 0;

        String prefix = path + File.separator
                + FilePathGenerator.filteredLayerName(trObj.layerName);

        File layerPath = new File(prefix);

        if (!layerPath.exists() || !layerPath.canWrite()) {
            throw new StorageException(prefix + " does not exist or is not writable.");
        }
        FilePathFilter fpf = new FilePathFilter(trObj);

        final String layerName = trObj.layerName;
        final String gridSetId = trObj.gridSetId;
        final String blobFormat = trObj.mimeType.getFormat();
        final String parameters = trObj.parameters;
        final Long parametersId = null;

        File[] srsZoomDirs = layerPath.listFiles(fpf);

        for (File srsZoom : srsZoomDirs) {
            int zoomLevel = FilePathGenerator.findZoomLevel(srsZoom.getName());
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
                        listeners.sendTileDeleted(layerName, gridSetId, blobFormat, parametersId,
                                x, y, zoomLevel, length);
                        count++;
                    }
                }

                String[] chk = imd.list();
                if (chk == null || chk.length == 0) {
                    imd.delete();
                }
            }

            String[] chk = srsZoom.list();
            if (chk == null || chk.length == 0) {
                srsZoom.delete();
                count++;
            }

        }

        log.debug("Truncated " + count + " tiles");

        return true;
    }

    public byte[] get(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, false);
        return readFile(fh);
    }

    public void put(TileObject stObj) throws StorageException {
        final File fh = getFileHandleTile(stObj, true);
        final boolean existed = fh.exists();
        final long oldSize = existed ? fh.length() : 0;
        writeFile(fh, stObj.getBlob());
        /*
         * This is important because listeners may be tracking tile existence
         */
        if (existed) {
            listeners.sendTileUpdated(stObj, oldSize);
        } else {
            listeners.sendTileStored(stObj);
        }
    }

    private File getFileHandleTile(TileObject stObj, boolean create) {
        String[] paths = null;
        try {
            paths = FilePathGenerator.tilePath(path, stObj.getLayerName(), stObj.getXYZ(),
                    stObj.getGridSetId(), MimeType.createFromFormat(stObj.getBlobFormat()),
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
            throw new StorageException(ioe.getMessage() + " for " + fh.getAbsolutePath());
        } finally {
            try {
                fis.close();
            } catch (IOException ioe) {
                throw new StorageException(ioe.getMessage() + " for " + fh.getAbsolutePath());
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
        OutputStream fos;
        try {
            fos = new FileOutputStream(fh);
        } catch (FileNotFoundException ioe) {
            throw new StorageException(ioe.getMessage() + " for " + fh.getAbsolutePath());
        }

        // fos = new NullOutputStream();
        // Write the stream
        try {
            fos.write(blob);
        } catch (IOException ioe) {
            throw new StorageException(ioe.getMessage() + " for " + fh.getAbsolutePath());
        } finally {
            try {
                fos.close();
            } catch (IOException ioe) {
                throw new StorageException(ioe.getMessage() + " for " + fh.getAbsolutePath());
            }
        }
    }

    private int writeFile(File fh, InputStream is) throws StorageException {
        // Open the output stream
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(fh);
        } catch (FileNotFoundException ioe) {
            throw new StorageException(ioe.getMessage() + " for " + fh.getAbsolutePath());
        }

        byte[] buffer = new byte[2048];
        int read = 0;
        int total = 0;
        try {
            while (read != -1) {
                read = is.read(buffer);
                if (read != -1) {
                    fos.write(buffer, 0, read);
                    total += read;
                }
            }
        } catch (IOException ioe) {
            throw new StorageException(ioe.getMessage() + " for " + fh.getAbsolutePath());
        } finally {
            try {
                fos.close();
                is.close();
            } catch (IOException ioe) {
                throw new StorageException(ioe.getMessage() + " for " + fh.getAbsolutePath());
            }
        }
        return read;
    }

    public void clear() throws StorageException {
        throw new StorageException("Not implemented yet!");
    }

    public void addListener(BlobStoreListener listener) {
        listeners.addListener(listener);
    }

    public boolean removeListener(BlobStoreListener listener) {
        return listeners.removeListener(listener);
    }

    /**
     * @see StorageBroker#accept(BlobStoreVisitor)
     */
    public void accept(BlobStoreVisitor visitor) {
        System.err.println(getClass().getSimpleName()
                + ".accept(BlobStoreVisitor) not yet implemented!!!!");
    }
}
