/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp / The Open Planning Project 2009
 */
package org.geowebcache.storage.blobstore.file;

import static org.geowebcache.storage.blobstore.file.FilePathUtils.filteredGridSetId;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.filteredLayerName;
import static org.geowebcache.util.FileUtils.listFilesNullSafe;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.io.FileResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.CompositeBlobStore;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.StorageObject.Status;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.util.FileUtils;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/** See BlobStore interface description for details */
public class FileBlobStore implements BlobStore {

    private static Log log =
            LogFactory.getLog(org.geowebcache.storage.blobstore.file.FileBlobStore.class);

    static final int DEFAULT_DISK_BLOCK_SIZE = 4096;

    static final String METADATA_GZIP_EXTENSION = ".gz";

    static final int METADATA_MAX_RW_ATTEMPTS = 10;

    public static final int BUFFER_SIZE = 32768;

    private final File stagingArea;

    private final String path;

    private int diskBlockSize = DEFAULT_DISK_BLOCK_SIZE;

    private final BlobStoreListenerList listeners = new BlobStoreListenerList();

    private FilePathGenerator pathGenerator;

    private File tmp;

    private ExecutorService deleteExecutorService;

    public FileBlobStore(DefaultStorageFinder defStoreFinder)
            throws StorageException, ConfigurationException {
        this(defStoreFinder.getDefaultPath());
    }

    public FileBlobStore(String rootPath) throws StorageException {
        this(rootPath, new DefaultFilePathGenerator(rootPath));
    }

    public FileBlobStore(String rootPath, FilePathGenerator pathGenerator) throws StorageException {
        this.path = rootPath;
        this.pathGenerator = pathGenerator;

        // prepare the root
        File fh = new File(path);
        fh.mkdirs();
        if (!fh.exists() || !fh.isDirectory() || !fh.canWrite()) {
            throw new StorageException(path + " is not writable directory.");
        }
        final boolean exists = new File(fh, "metadata.properties").exists();
        boolean empty = true;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(fh.toPath())) {
            for (@SuppressWarnings("unused") Path p : ds) {
                empty = false;
                break;
            }
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException("Error while checking that " + rootPath + " is empty", e);
        }

        CompositeBlobStore.checkSuitability(rootPath, exists, empty);

        // and the temporary directory
        tmp = new File(path, "tmp");
        try {
            Files.createDirectories(tmp.toPath());
        } catch (IOException e) {
            throw new StorageException(tmp.getPath() + " is not writable directory.", e);
        }

        File metadataFile = new File(path, "metadata.properties");
        try {
            // TODO This is just to provide a hint that this is a GWC blobstore for now.
            // In future it should store key value pairs.
            metadataFile.createNewFile();
        } catch (IOException e) {
            log.error("Error while writing blobstore metadata file " + metadataFile.getPath(), e);
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
            throw new IllegalStateException(
                    "Staging area is not writable or is not a directory: "
                            + stagingArea.getAbsolutePath());
        }
        File[] pendings = listFilesNullSafe(stagingArea);
        for (File directory : pendings) {
            if (directory.isDirectory()) {
                deletePending(directory);
            }
        }
    }

    private void deletePending(final File pendingDeleteDirectory) {
        deleteExecutorService.submit(new DefferredDirectoryDeleteTask(pendingDeleteDirectory));
    }

    private void createDeleteExecutorService() {
        CustomizableThreadFactory tf;
        tf = new CustomizableThreadFactory("GWC FileStore delete directory thread-");
        tf.setDaemon(true);
        tf.setThreadPriority(Thread.MIN_PRIORITY);
        deleteExecutorService = Executors.newFixedThreadPool(1);
    }

    /** Destroy method for Spring */
    public void destroy() {
        if (deleteExecutorService != null) {
            deleteExecutorService.shutdown();
        }
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
                log.warn(
                        "Exception occurred while deleting '" + directory.getAbsolutePath() + "'",
                        e);
            } catch (InterruptedException e) {
                log.info(
                        "FileStore delete background service interrupted while deleting '"
                                + directory.getAbsolutePath()
                                + "'. Process will be resumed at next start up");
                Thread.currentThread().interrupt();
            }
        }

        private void deleteDirectory(File directory) throws IOException, InterruptedException {
            if (!directory.exists()) {
                return;
            }
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            File[] files = listFilesNullSafe(directory);
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

    /** @see org.geowebcache.storage.BlobStore#delete(java.lang.String) */
    public boolean delete(final String layerName) throws StorageException {
        final File source = getLayerPath(layerName);
        final String target = filteredLayerName(layerName);

        boolean ret = stageDelete(source, target);

        this.listeners.sendLayerDeleted(layerName);
        return ret;
    }

    private boolean stageDelete(final File source, final String targetName)
            throws StorageException {

        if (!source.exists() || !source.canWrite()) {
            log.info(source + " does not exist or is not writable");
            return false;
        }

        if (!stagingArea.exists() && !stagingArea.mkdirs()) {
            throw new StorageException(
                    "Can't create staging directory for deletes: " + stagingArea.getAbsolutePath());
        }

        File tmpFolder = new File(stagingArea, targetName);
        int tries = 0;
        while (tmpFolder.exists()) {
            ++tries;
            String dirName = filteredLayerName(targetName + "." + tries);
            tmpFolder = new File(stagingArea, dirName);
        }
        boolean renamed = FileUtils.renameFile(source, tmpFolder);
        if (!renamed) {
            throw new IllegalStateException(
                    "Can't rename "
                            + source.getAbsolutePath()
                            + " to "
                            + tmpFolder.getAbsolutePath()
                            + " for deletion");
        }
        deletePending(tmpFolder);
        return true;
    }

    /**
     * @see org.geowebcache.storage.BlobStore#deleteByGridsetId(java.lang.String, java.lang.String)
     */
    public boolean deleteByGridsetId(final String layerName, final String gridSetId)
            throws StorageException {

        final File layerPath = getLayerPath(layerName);
        if (!layerPath.exists() || !layerPath.canWrite()) {
            log.info(layerPath + " does not exist or is not writable");
            return false;
        }
        final String filteredGridSetId = filteredGridSetId(gridSetId);

        FileFilter filter =
                new FileFilter() {
                    public boolean accept(File pathname) {
                        if (!pathname.isDirectory()) {
                            return false;
                        }
                        String dirName = pathname.getName();
                        return dirName.startsWith(filteredGridSetId);
                    }
                };
        File[] gridSubsetCaches = listFilesNullSafe(layerPath, filter);

        for (File gridSubsetCache : gridSubsetCaches) {
            String target = filteredLayerName(layerName) + "_" + gridSubsetCache.getName();
            stageDelete(gridSubsetCache, target);
        }

        listeners.sendGridSubsetDeleted(layerName, gridSetId);

        return true;
    }

    /**
     * Renames the layer directory for layer {@code oldLayerName} to {@code newLayerName}
     *
     * @return true if the directory for the layer was renamed, or the original directory didn't
     *     exist in first place. {@code false} if the original directory exists but can't be renamed
     *     to the target directory
     * @throws StorageException if the target directory already exists
     * @see org.geowebcache.storage.BlobStore#rename
     */
    public boolean rename(final String oldLayerName, final String newLayerName)
            throws StorageException {
        final File oldLayerPath = getLayerPath(oldLayerName);
        final File newLayerPath = getLayerPath(newLayerName);

        if (newLayerPath.exists()) {
            throw new StorageException(
                    "Can't rename layer directory "
                            + oldLayerPath
                            + " to "
                            + newLayerPath
                            + ". Target directory already exists");
        }
        if (!oldLayerPath.exists()) {
            this.listeners.sendLayerRenamed(oldLayerName, newLayerName);
            return true;
        }
        if (!oldLayerPath.canWrite()) {
            log.info(oldLayerPath + " is not writable");
            return false;
        }
        boolean renamed = FileUtils.renameFile(oldLayerPath, newLayerPath);
        if (renamed) {
            this.listeners.sendLayerRenamed(oldLayerName, newLayerName);
        } else {
            throw new StorageException(
                    "Couldn't rename layer directory " + oldLayerPath + " to " + newLayerPath);
        }
        return renamed;
    }

    private File getLayerPath(String layerName) {
        String prefix = path + File.separator + filteredLayerName(layerName);

        File layerPath = new File(prefix);
        return layerPath;
    }

    /** Delete a particular tile */
    public boolean delete(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, null);
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
            stObj.setBlobSize((int) padSize(length));
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

    /** Delete tiles within a range. */
    public boolean delete(TileRange trObj) throws StorageException {

        String prefix = path + File.separator + filteredLayerName(trObj.getLayerName());

        final File layerPath = new File(prefix);

        // If it wasn't there to be deleted,
        if (!layerPath.exists()) {
            return true;
        }

        // We either want to delete it, or stuff within it
        if (!layerPath.isDirectory() || !layerPath.canWrite()) {
            throw new StorageException(prefix + " does is not a directory or is not writable.");
        }

        final String layerName = trObj.getLayerName();
        final String gridSetId = trObj.getGridSetId();
        final String blobFormat = trObj.getMimeType().getFormat();
        final String parametersId = trObj.getParametersId();

        AtomicLong count = new AtomicLong();
        pathGenerator.visitRange(
                layerPath,
                trObj,
                new TileFileVisitor() {

                    @Override
                    public void visitFile(File tile, long x, long y, int z) {
                        long length = tile.length();
                        boolean deleted = tile.delete();
                        if (deleted) {
                            listeners.sendTileDeleted(
                                    layerName,
                                    gridSetId,
                                    blobFormat,
                                    parametersId,
                                    x,
                                    y,
                                    z,
                                    padSize(length));
                            count.incrementAndGet();
                        }
                    }

                    @Override
                    public void postVisitDirectory(File dir) {
                        // will delete only if empty
                        dir.delete();
                    }
                });

        log.info("Truncated " + count + " tiles");

        return true;
    }

    /**
     * Set the blob property of a TileObject.
     *
     * @param stObj the tile to load. Its setBlob() method will be called.
     * @return true if successful, false otherwise
     */
    public boolean get(TileObject stObj) throws StorageException {
        File fh = getFileHandleTile(stObj, null);
        if (!fh.exists()) {
            stObj.setStatus(Status.MISS);
            return false;
        } else {
            Resource resource = readFile(fh);
            stObj.setBlob(resource);
            stObj.setCreated(resource.getLastModified());
            stObj.setBlobSize((int) resource.getSize());
            return true;
        }
    }

    /** Store a tile. */
    public void put(TileObject stObj) throws StorageException {

        // An update to ParameterMap file is required !!!
        Runnable upm =
                () -> {
                    this.persistParameterMap(stObj);
                };
        final File fh = getFileHandleTile(stObj, upm);
        final long oldSize = fh.length();
        final boolean existed = oldSize > 0;

        writeFile(fh, stObj, existed);

        // mark the last modification as the tile creation time if set, otherwise
        // we'll leave it to the writing time
        if (stObj.getCreated() > 0) {
            try {
                fh.setLastModified(stObj.getCreated());
            } catch (Exception e) {
                log.debug("Failed to set the last modified time to match the tile request time", e);
            }
        }

        /*
         * This is important because listeners may be tracking tile existence
         */
        stObj.setBlobSize((int) padSize(stObj.getBlobSize()));
        if (existed) {
            listeners.sendTileUpdated(stObj, padSize(oldSize));
        } else {
            listeners.sendTileStored(stObj);
        }
    }

    private File getFileHandleTile(TileObject stObj, Runnable onTileFolderCreation)
            throws StorageException {
        final MimeType mimeType;
        try {
            mimeType = MimeType.createFromFormat(stObj.getBlobFormat());
        } catch (MimeException me) {
            log.error(me.getMessage());
            throw new RuntimeException(me);
        }

        final File tilePath;
        try {
            tilePath = pathGenerator.tilePath(stObj, mimeType);
        } catch (GeoWebCacheException e) {
            throw new StorageException("Failed to compute file path", e);
        }

        // Check if it's required to create tile folder
        if (onTileFolderCreation != null) {
            log.debug("Creating parent tile folder and updating ParameterMap");
            File parent = tilePath.getParentFile();
            mkdirs(parent, stObj);
            onTileFolderCreation.run();
        }

        return tilePath;
    }

    private Resource readFile(File fh) throws StorageException {
        if (!fh.exists()) {
            return null;
        }
        return new FileResource(fh);
    }

    private void writeFile(File target, TileObject stObj, boolean existed) throws StorageException {
        // first write to temp file
        tmp.mkdirs();
        File temp = new File(tmp, UUID.randomUUID().toString());

        try {
            // Open the output stream and read the blob into the tile
            try (FileOutputStream fos = new FileOutputStream(temp);
                    FileChannel channel = fos.getChannel()) {
                stObj.getBlob().transferTo(channel);
            } catch (IOException ioe) {
                throw new StorageException(ioe.getMessage() + " for " + target.getAbsolutePath());
            }

            // rename to final position. This will fail if another GWC also wrote this
            // file, in such case we'll just eliminate this one
            if (FileUtils.renameFile(temp, target)) {
                temp = null;
            } else if (existed) {
                // if we are trying to overwrite and old tile, on windows that might fail... delete
                // and rename instead
                if (target.delete() && FileUtils.renameFile(temp, target)) {
                    temp = null;
                }
            }

        } finally {

            if (temp != null) {
                log.warn(
                        "Tile "
                                + target.getPath()
                                + " was already written by another thread/process");
                temp.delete();
            }
        }
    }

    protected void persistParameterMap(TileObject stObj) {
        if (Objects.nonNull(stObj.getParametersId())) {
            putLayerMetadata(
                    stObj.getLayerName(),
                    "parameters." + stObj.getParametersId(),
                    ParametersUtils.getKvp(stObj.getParameters()));
        }
    }

    public void clear() throws StorageException {
        throw new StorageException("Not implemented yet!");
    }

    /** Add an event listener */
    public void addListener(BlobStoreListener listener) {
        listeners.addListener(listener);
    }

    /** Remove an event listener */
    public boolean removeListener(BlobStoreListener listener) {
        return listeners.removeListener(listener);
    }

    /**
     * This method will recursively create the missing directories and call the listeners
     * directoryCreated method for each created directory.
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
            value = urlDecUtf8(value);
        }
        return value;
    }

    private static String urlDecUtf8(String value) {
        try {
            value = URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    /**
     * @see org.geowebcache.storage.BlobStore#putLayerMetadata(java.lang.String, java.lang.String,
     *     java.lang.String)
     */
    public void putLayerMetadata(final String layerName, final String key, final String value) {
        // Get current metadataFile
        final File metadataFile = getMetadataFile(layerName);
        // Get last modification time for metadata file
        long lastModDate = metadataFile.lastModified();
        // Get metadata content from current metadataFile
        Properties metadata = getLayerMetadata(metadataFile);

        // Evaluate updating key arg related values (remove/add/update or skip)
        boolean skipUpdate = false;
        // We want to avoid writing metadata file if nothing changed, need to check if required
        if (null == value) {
            metadata.remove(key);
        } else {
            String currValue = metadata.getProperty(key);
            String encodedValue = null;
            try {
                encodedValue = URLEncoder.encode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            skipUpdate =
                    currValue != null && encodedValue != null && currValue.equals(encodedValue);
            // If existing value for key is the same as value arg, then avoid to write it
            if (!skipUpdate) {
                metadata.setProperty(key, encodedValue);
            }
        }

        // We call to save metadata only if has changed
        if (!skipUpdate) {
            writeMetadataOptimisticLock(metadataFile, lastModDate, metadata);
        }
    }

    private void writeMetadataOptimisticLock(
            final File metadataFile, long lastModDate, Properties metadata) {
        // Write metadata in a temp file
        File tempFile = null;
        try {
            tempFile = writeTempMetadataFile(metadata);
        } catch (IOException e) {
            log.error("Cannot create temporary file for " + metadataFile.getPath());
            throw new UncheckedIOException(e);
        }
        // Attempt to rename tmp file to metadata file
        int attempts = 0;
        while (attempts < FileBlobStore.METADATA_MAX_RW_ATTEMPTS) {
            // Read current metadatafile date modification (currentModTime)
            long currentModDate = metadataFile.lastModified();
            // Note: lastModData == 0 then file does not existed when getting lastModified
            if (lastModDate == 0 || lastModDate == currentModDate) {
                metadataFile.getParentFile().mkdirs();
                // atomic rename of temporary metadata file with getMetadataFilename
                // file, in such case we'll just eliminate this one
                if (FileUtils.renameFile(tempFile, metadataFile)) {
                    log.debug("Temporary file renamed successfully");
                    break;
                } else {
                    // Renaming failed !
                    attempts++;
                    log.debug(
                            "Reattempting to write metadata file, because an error while renaming metadata file "
                                    + metadataFile.getPath());
                }
            } else {
                // Try again since some other GWC updated the file in middle of writing process
                attempts++;
                log.debug("Reattempting to write metadata file since timestamp changed");
            }
        }
    }

    private File writeTempMetadataFile(Properties metadata) throws IOException {
        tmp.mkdirs();
        final File metadataFile =
                File.createTempFile("tmp", FileBlobStore.METADATA_GZIP_EXTENSION, tmp);
        return this.writeMetadataFile(metadata, metadataFile);
    }

    /**
     * Writes a Metadatafile with metadata parameter content
     *
     * @param metadata
     * @return temporal file or null if failed
     * @throws IOException
     */
    private File writeMetadataFile(Properties metadata, File metadataFile) throws IOException {
        final String lockObj = metadataFile.getAbsolutePath().intern();
        synchronized (lockObj) {
            if (!metadataFile.getParentFile().exists()) {
                metadataFile.getParentFile().mkdirs();
            }
            try (OutputStream out = new FileOutputStream(metadataFile);
                    Writer writer = new OutputStreamWriter(new GZIPOutputStream(out), "UTF-8"); ) {

                String comments = "auto generated file, do not edit by hand";
                // Save as a gzip file
                metadata.store(writer, comments);
                return metadataFile;

            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Properties getUncompressedLayerMetadata(final File metadataFile) {
        Properties properties = new Properties();
        long lastModified = metadataFile.lastModified();
        final String lockObj = metadataFile.getAbsolutePath().intern();
        synchronized (lockObj) {
            if (metadataFile.exists()) {
                try (FileInputStream in = new FileInputStream(metadataFile); ) {
                    int attempts = 0;
                    while (attempts < FileBlobStore.METADATA_MAX_RW_ATTEMPTS) {
                        properties.load(in);
                        // Read current metadatafile date modification (currentModTime)
                        long currentModDate = metadataFile.lastModified();
                        if (lastModified == currentModDate) {
                            break;
                        } else {
                            // Try again since some other GWC updated the file
                            attempts++;
                            log.debug("Reattempting to read metadata file since timestamp changed");
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                log.debug("Uncompressed metadata file does not exists: " + metadataFile.getPath());
            }
        }
        return properties;
    }

    private Properties getLayerMetadata(final File metadataFile) {
        Properties properties = new Properties();
        long lastModified = metadataFile.lastModified();
        final String lockObj = metadataFile.getAbsolutePath().intern();
        synchronized (lockObj) {
            if (metadataFile.exists()) {
                try (FileInputStream in = new FileInputStream(metadataFile);
                        GZIPInputStream gzipIn = new GZIPInputStream(in); ) {
                    int attempts = 0;
                    while (attempts < FileBlobStore.METADATA_MAX_RW_ATTEMPTS) {
                        try {
                            properties.load(gzipIn);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        // Read current metadatafile date modification (currentModTime)
                        long currentModDate = metadataFile.lastModified();
                        if (lastModified == currentModDate) {
                            break;
                        } else {
                            // Try again since some other GWC updated the file
                            attempts++;
                            log.debug("Reattempting to read metadata file since timestamp changed");
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                log.debug("Metadata file does not exists: " + metadataFile.getPath());
            }
        }
        return properties;
    }

    private Properties getLayerMetadata(final String layerName) {
        final File metadataFile = getMetadataFile(layerName);
        return this.getLayerMetadata(metadataFile);
    }

    private String getMetadataFilename(boolean compressedName) {
        String metadataFilename = "metadata.properties";
        if (compressedName) {
            metadataFilename += FileBlobStore.METADATA_GZIP_EXTENSION;
        }
        return metadataFilename;
    }

    /**
     * Returns the File related to metadata.properties
     *
     * @param layerName
     * @return metadata file (compressed or not, depending if it's present uncompressed)
     */
    private File getMetadataFile(final String layerName) {
        File layerPath = getLayerPath(layerName);
        // Get compressed metadata filename
        String metadataFilename = this.getMetadataFilename(true);
        File metadataFile = new File(layerPath, metadataFilename);
        // If compressed metadata exists then avoid backwards compatibility work
        if (metadataFile.exists()) {
            return metadataFile;
        }
        // Backwards compatibility: move uncompressed metadata.properties files to compressed
        // version
        String oldMetadataFilename = this.getMetadataFilename(false);
        File oldMetadataFile = new File(layerPath, oldMetadataFilename);
        if (oldMetadataFile.exists()) {
            // Uncompressed file exists, move to the compressed format file
            Properties oldProperties = this.getUncompressedLayerMetadata(oldMetadataFile);
            try {
                File compressedNewFile = this.writeMetadataFile(oldProperties, metadataFile);
                // Remove the older format
                oldMetadataFile.delete();
                return compressedNewFile;
            } catch (IOException e) {
                log.error(
                        "Backporting metadata.properties - Failure creating new compressed file or deleting uncompressed one "
                                + metadataFile.getPath()
                                + '-'
                                + e.getMessage());
                throw new UncheckedIOException(e);
            }
        }
        return metadataFile;
    }

    @Override
    public boolean layerExists(String layerName) {
        return getLayerPath(layerName).exists();
    }

    /**
     * Specify the file system block size, used to pad out tile lenghts to whole blocks when
     * reporting {@link BlobStoreListener#tileDeleted tileDeleted}, {@link
     * BlobStoreListener#tileStored tileStored}, or {@link BlobStoreListener#tileUpdated
     * tileUpdated} events.
     *
     * @param fileSystemBlockSize the size of a filesystem block; must be a positive integer,
     *     usually a power of 2 greater or equal to 512.
     */
    public void setBlockSize(int fileSystemBlockSize) {
        Preconditions.checkArgument(fileSystemBlockSize > 0);
        this.diskBlockSize = fileSystemBlockSize;
    }

    /**
     * Pads the size of a tile to whole filesystem blocks
     *
     * @param fileSize the size of the tile file as reported by {@link File#length()}
     * @return {@code fileSize} padded to whole blocks as per {@link #diskBlockSize}
     */
    private long padSize(long fileSize) {

        final int blockSize = this.diskBlockSize;

        long actuallyUsedStorage = blockSize * (int) Math.ceil((double) fileSize / blockSize);

        return actuallyUsedStorage;
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId)
            throws StorageException {

        final File layerPath = getLayerPath(layerName);
        if (!layerPath.exists() || !layerPath.canWrite()) {
            log.info(layerPath + " does not exist or is not writable");
            return false;
        }

        File[] parameterCaches =
                listFilesNullSafe(
                        layerPath,
                        (pathname) -> {
                            if (!pathname.isDirectory()) {
                                return false;
                            }
                            String dirName = pathname.getName();
                            return dirName.endsWith(parametersId);
                        });

        for (File parameterCache : parameterCaches) {
            String target = filteredLayerName(layerName) + "_" + parameterCache.getName();
            stageDelete(parameterCache, target);
        }

        listeners.sendParametersDeleted(layerName, parametersId);

        return true;
    }

    private Stream<Path> layerChildStream(
            final String layerName, DirectoryStream.Filter<Path> filter) throws IOException {
        final File layerPath = getLayerPath(layerName);
        if (!layerPath.exists()) {
            return Stream.of();
        }
        @SuppressWarnings("PMD.CloseResource") // wrapped and closed in the return value
        final DirectoryStream<Path> layerDirStream =
                Files.newDirectoryStream(layerPath.toPath(), filter);
        return StreamSupport.stream(layerDirStream.spliterator(), false)
                .onClose( // Delegate closing so that when the returned stream is closed, so is the
                        // underlying DirectoryStream
                        () -> {
                            try {
                                layerDirStream.close();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
    }

    public boolean isParameterIdCached(String layerName, final String parametersId)
            throws IOException {
        try (Stream<Path> layerChildStream =
                layerChildStream(
                        layerName, (p) -> Files.isDirectory(p) && p.endsWith(parametersId))) {
            return layerChildStream.findAny().isPresent();
        }
    }

    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        Properties p = getLayerMetadata(layerName);
        return getParameterIds(layerName)
                .stream()
                .collect(
                        Collectors.toMap(
                                (id) -> id,
                                (id) -> {
                                    String kvp = p.getProperty("parameters." + id);
                                    if (Objects.isNull(kvp)) {
                                        return Optional.empty();
                                    }
                                    kvp = urlDecUtf8(kvp);
                                    return Optional.of(ParametersUtils.getMap(kvp));
                                }));
    }

    static final int paramIdLength =
            ParametersUtils.getId(Collections.singletonMap("A", "B")).length();

    @Override
    public Set<String> getParameterIds(String layerName) {
        try (Stream<Path> layerChildStream =
                layerChildStream(layerName, (p) -> Files.isDirectory(p))) {
            return layerChildStream
                    .map(p -> p.getFileName().toString())
                    .map(s -> s.substring(s.lastIndexOf('_') + 1))
                    .filter(s -> s.length() == paramIdLength) // Zoom level should never be the same
                    // length so this should be safe
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
