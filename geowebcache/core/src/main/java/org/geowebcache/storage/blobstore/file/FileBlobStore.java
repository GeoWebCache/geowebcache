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
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.io.FileResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
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

    public FileBlobStore(DefaultStorageFinder defStoreFinder) throws ConfigurationException {
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
    }

    /**
     * Renames the layer directory for layer {@code oldLayerName} to {@code newLayerName}
     * 
     * @return true if the directory for the layer was renamed, or the original directory didn't
     *         exist in first place. {@code false} if the original directory exists but can't be
     *         renamed to the target directory
     * @throws StorageException
     *             if the target directory already exists
     * @see org.geowebcache.storage.BlobStore#rename
     */
    public boolean rename(final String oldLayerName, final String newLayerName)
            throws StorageException {
        final File oldLayerPath = getLayerPath(oldLayerName);
        final File newLayerPath = getLayerPath(newLayerName);

        if (newLayerPath.exists()) {
            throw new StorageException("Can't rename layer directory " + oldLayerPath + " to "
                    + newLayerPath + ". Target directory already exists");
        }
        if (!oldLayerPath.exists()) {
            this.listeners.sendLayerRenamed(oldLayerName, newLayerName);
            return true;
        }
        if (!oldLayerPath.canWrite()) {
            log.info(oldLayerPath + " is not writable");
            return false;
        }
        boolean renamed = oldLayerPath.renameTo(newLayerPath);
        if (renamed) {
            this.listeners.sendLayerRenamed(oldLayerName, newLayerName);
        } else {
            throw new StorageException("Couldn't rename layer directory " + oldLayerPath + " to "
                    + newLayerPath);
        }
        return renamed;
    }

    private File getLayerPath(String layerName) {
        String prefix = path + File.separator + FilePathGenerator.filteredLayerName(layerName);

        File layerPath = new File(prefix);
        return layerPath;
    }

    public boolean delete(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, false);
        boolean ret = false;
        // we call fh.length() here to check wthether the file exists and its length in a single
        // operation cause lots of calls to exists() may raise the file system cache usage to the
        // ceiling. File.length() returns 0 if the file does not exist anyway
        final long length = fh.length();
        final boolean exists = length > 0;
        if (exists) {
            if (!fh.delete()) {
                throw new StorageException("Unable to delete " + fh.getAbsolutePath());
            }
            stObj.setBlobSize((int) length);
            listeners.sendTileDeleted(stObj);

            ret = true;
        } else {
            log.trace("delete unexistant file " + fh.toString());
        }

        // Look at the parent directory to prune it if empty
        File parentDir = fh.getParentFile();
        // Try deleting the directory (will not do it if the directory contains files)
        parentDir.delete();

        return ret;
    }

    public boolean delete(TileRange trObj) throws StorageException {
        int count = 0;

        String prefix = path + File.separator
                + FilePathGenerator.filteredLayerName(trObj.layerName);

        final File layerPath = new File(prefix);

        if (!layerPath.exists()) {
            return true;
        }
        if (!layerPath.isDirectory() || !layerPath.canWrite()) {
            throw new StorageException(prefix + " does is not a directory or is not writable.");
        }
        FilePathFilter fpf = new FilePathFilter(trObj);

        final String layerName = trObj.layerName;
        final String gridSetId = trObj.gridSetId;
        final String blobFormat = trObj.mimeType.getFormat();
        // FRD trObj.getParametersId();
        final Long parametersId = trObj.getParametersId();

        File[] srsZoomDirs = layerPath.listFiles(fpf);

        for (File srsZoomParamId : srsZoomDirs) {
            int zoomLevel = FilePathGenerator.findZoomLevel(srsZoomParamId.getName());
            File[] intermediates = srsZoomParamId.listFiles(fpf);

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

                // Try deleting the directory (will be done only if the directory is empty)
                if (imd.delete()) {
                    // listeners.sendDirectoryDeleted(layerName);
                }
            }

            // Try deleting the zoom directory (will be done only if the directory is empty)
            if (srsZoomParamId.delete()) {
                count++;
                // listeners.sendDirectoryDeleted(layerName);
            }
        }

        log.info("Truncated " + count + " tiles");

        return true;
    }

    public Resource get(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, false);
        return readFile(fh);
    }

    public void put(TileObject stObj) throws StorageException {
        final File fh = getFileHandleTile(stObj, true);
        final long oldSize = fh.length();
        final boolean existed = oldSize > 0;
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
        final MimeType mimeType;
        try {
            mimeType = MimeType.createFromFormat(stObj.getBlobFormat());
        } catch (MimeException me) {
            log.error(me.getMessage());
            throw new RuntimeException(me);
        }

        final String layerName = stObj.getLayerName();
        final long[] xyz = stObj.getXYZ();
        final String gridSetId = stObj.getGridSetId();
        final long parametersId = stObj.getParametersId();

        final File tilePath = FilePathGenerator.tilePath(path, layerName, xyz, gridSetId, mimeType,
                parametersId);

        if (create) {
            File parent = tilePath.getParentFile();
            mkdirs(parent, stObj);
        }

        return tilePath;
    }

    private Resource readFile(File fh) throws StorageException {
        if (!fh.exists()) {
            return null;
        }
        return new FileResource(fh);
    }

    private void writeFile(File target, Resource source) throws StorageException {
        // Open the output stream
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(target);
        } catch (FileNotFoundException ioe) {
            throw new StorageException(ioe.getMessage() + " for " + target.getAbsolutePath());
        }

        FileChannel channel = fos.getChannel();
        try {
            source.transferTo(channel);
        } catch (IOException ioe) {
            throw new StorageException(ioe.getMessage() + " for " + target.getAbsolutePath());
        } finally {
            try {
                channel.close();
            } catch (IOException ioe) {
                throw new StorageException(ioe.getMessage() + " for " + target.getAbsolutePath());
            }
        }
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
     * This method will recursively create the missing directories and call the listeners
     * directoryCreated method for each created directory.
     * 
     * @param path
     * @return
     */
    private boolean mkdirs(File path, TileObject stObj) {
        /* If the terminal directory already exists, answer false */
        if (path.exists()) {
            return false;
        }
        /* If the receiver can be created, answer true */
        if (path.mkdir()) {
            // listeners.sendDirectoryCreated(stObj);
            return true;
        }
        String parentDir = path.getParent();
        /* If there is no parent and we were not created, answer false */
        if (parentDir == null) {
            return false;
        }
        /* Otherwise, try to create a parent directory and then this directory */
        mkdirs(new File(parentDir), stObj);
        if (path.mkdir()) {
            // listeners.sendDirectoryCreated(stObj);
            return true;
        }
        return false;
    }

    /**
     * @see org.geowebcache.storage.BlobStore#getLayerMetadata(java.lang.String, java.lang.String)
     */
    public String getLayerMetadata(final String layerName, final String key) {
        Properties metadata = getLayerMetadata(layerName);
        String value = metadata.getProperty(key);
        if (value != null) {
            try {
                value = URLDecoder.decode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        return value;
    }

    /**
     * @see org.geowebcache.storage.BlobStore#putLayerMetadata(java.lang.String, java.lang.String,
     *      java.lang.String)
     */
    public void putLayerMetadata(final String layerName, final String key, final String value) {
        Properties metadata = getLayerMetadata(layerName);
        if (null == value) {
            metadata.remove(key);
        } else {
            try {
                metadata.setProperty(key, URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        final File metadataFile = getMetadataFile(layerName);

        final String lockObj = metadataFile.getAbsolutePath().intern();
        synchronized (lockObj) {
            OutputStream out;
            try {
                if (!metadataFile.getParentFile().exists()) {
                    metadataFile.getParentFile().mkdirs();
                }
                out = new FileOutputStream(metadataFile);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            try {
                String comments = "auto generated file, do not edit by hand";
                metadata.store(out, comments);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    out.close();
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    private Properties getLayerMetadata(final String layerName) {
        final File metadataFile = getMetadataFile(layerName);
        Properties properties = new Properties();
        final String lockObj = metadataFile.getAbsolutePath().intern();
        synchronized (lockObj) {
            if (metadataFile.exists()) {
                FileInputStream in;
                try {
                    in = new FileInputStream(metadataFile);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
                try {
                    properties.load(in);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        log.warn(e.getMessage(), e);
                    }
                }
            }
        }
        return properties;
    }

    private File getMetadataFile(final String layerName) {
        File layerPath = getLayerPath(layerName);
        File metadataFile = new File(layerPath, "metadata.properties");
        return metadataFile;
    }

}
