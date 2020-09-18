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
 * @author Jose Macchi / Geosolutions 2009
 */
package org.geowebcache.storage.blobstore.file;

import static org.geowebcache.storage.blobstore.file.FilePathUtils.filteredLayerName;

import com.google.common.hash.Hashing;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.util.FileUtils;
import org.geowebcache.util.SuppressFBWarnings;

public class LayerMetadataStore {

    private static Log log =
            LogFactory.getLog(org.geowebcache.storage.blobstore.file.LayerMetadataStore.class);

    public static final String PROPERTY_METADATA_MAX_RW_ATTEMPTS =
            "gwc.fileblobstore.maxRWAttempts";

    static final int METADATA_MAX_RW_ATTEMPTS =
            Integer.parseInt(System.getProperty(PROPERTY_METADATA_MAX_RW_ATTEMPTS, "10"));

    static final String METADATA_GZIP_EXTENSION = ".gz";

    private final String path;

    private File tmp;

    /** number of locks, make it configurable maybe */
    private static final int lockShardSize = 32;

    /** handling of local-process concurrent access to layer metadata files */
    private ReadWriteLock[] locks =
            IntStream.range(0, lockShardSize)
                    .mapToObj(i -> new ReentrantReadWriteLock())
                    .toArray(ReadWriteLock[]::new);

    public LayerMetadataStore(String rootPath, File tmpPath) {
        this.path = rootPath;
        this.tmp = tmpPath;
    }

    public Map<String, String> getLayerMetadata(String layerName) {
        Properties props = loadLayerMetadata(layerName);
        HashMap<String, String> map = new HashMap<>();
        props.forEach((k, v) -> map.put((String) k, (String) v));
        return map;
    }

    public String getEntry(final String layerName, final String key) {
        Properties metadata = loadLayerMetadata(layerName);
        String value = metadata.getProperty(key);
        return value == null ? value : urlDecUtf8(value);
    }

    /**
     * @throws UnsupportedEncodingException
     * @see org.geowebcache.storage.BlobStore#putLayerMetadata(java.lang.String, java.lang.String,
     *     java.lang.String)
     */
    public void putEntry(final String layerName, final String key, final String value)
            throws UnsupportedEncodingException {
        final File metadataFile = resolveMetadataFile(layerName);
        Properties metadata = loadLayerMetadata(metadataFile);
        // Current last modification time
        final long lastModDate = metadataFile.lastModified();

        boolean doUpdate;
        String encodedValue;
        if (null == value) {
            doUpdate = metadata.containsKey(key);
            encodedValue = null;
        } else {
            encodedValue = URLEncoder.encode(value, "UTF-8");
            doUpdate = !Objects.equals(encodedValue, metadata.getProperty(key));
        }
        if (doUpdate) {
            writeMetadataOptimisticLock(key, encodedValue, metadataFile, metadata, lastModDate);
        }
    }

    private File getLayerPath(String layerName) {
        String prefix = path + File.separator + filteredLayerName(layerName);
        File layerPath = new File(prefix);
        return layerPath;
    }

    private static String urlDecUtf8(String value) {
        try {
            value = URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    private int resolveLockBucket(File file) {
        long consistentFileNameHash =
                Hashing.farmHashFingerprint64()
                        .hashString(file.getAbsolutePath(), StandardCharsets.UTF_8)
                        .asLong();
        int bucket = Hashing.consistentHash(consistentFileNameHash, locks.length);
        return bucket;
    }

    private ReadWriteLock getLock(File file) {
        return locks[resolveLockBucket(file)];
    }

    /**
     * Performs the actual update of the metatada, making sure only the provided key/value pair is
     * updated in case another process modified the metadata file since it was loaded by the caller
     * code
     */
    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE")
    private void writeMetadataOptimisticLock(
            final String key,
            final String value,
            final File metadataFile,
            Properties metadata,
            long lastModified) {
        final ReadWriteLock rwLock = getLock(metadataFile);
        final int maxAttempts = LayerMetadataStore.METADATA_MAX_RW_ATTEMPTS;

        rwLock.writeLock().lock();
        try {
            createParentIfNeeded(metadataFile);
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                if (lastModified == metadataFile.lastModified()) {
                    metadata = loadLayerMetadata(metadataFile);
                    metadata.compute(key, (k, oldValue) -> value); // removes mapping if value==null
                    File tempFile = writeTempMetadataFile(metadata);
                    if (FileUtils.renameFile(tempFile, metadataFile)) {
                        log.debug("Temporary file renamed successfully");
                        return;
                    } else {
                        log.info(
                                "Reattempting to write metadata file, because an error while renaming metadata file "
                                        + metadataFile.getPath());
                    }
                    tempFile.delete();
                } else {
                    log.debug("Reattempting to write metadata file since timestamp changed");
                }
                // another process beat us, reload
                // next line triggers a false-positive DLS_DEAD_LOCAL_STORE
                metadata = loadLayerMetadata(metadataFile);
                lastModified = metadataFile.lastModified();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private File writeTempMetadataFile(Properties metadata) {
        tmp.mkdirs();
        try {
            final File metadataFile =
                    File.createTempFile("tmp", LayerMetadataStore.METADATA_GZIP_EXTENSION, tmp);
            return this.writeMetadataFile(metadata, metadataFile);
        } catch (IOException e) {
            log.error("Cannot create temporary file");
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes a Metadatafile with metadata parameter content
     *
     * @param metadata
     * @return temporal file or null if failed
     * @throws IOException
     */
    private File writeMetadataFile(Properties metadata, File metadataFile) throws IOException {
        final ReadWriteLock lock = getLock(metadataFile);
        lock.writeLock().lock();
        try {
            createParentIfNeeded(metadataFile);
            String comments = "auto generated file, do not edit by hand";
            try (Writer writer = compressingWriter(metadataFile)) {
                metadata.store(writer, comments);
            }
        } finally {
            lock.writeLock().unlock();
        }
        return metadataFile;
    }

    private void createParentIfNeeded(File metadataFile) {
        File parentDir = metadataFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IllegalStateException(
                    "Unable to create parent directory " + parentDir.getAbsolutePath());
        }
    }

    private Writer compressingWriter(File file) throws FileNotFoundException, IOException {
        return new OutputStreamWriter(
                new GZIPOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8);
    }

    private Properties getUncompressedLayerMetadata(final File metadataFile) {
        return loadLayerMetadata(metadataFile, this::open);
    }

    private Properties loadLayerMetadata(
            File metadataFile, Function<File, InputStream> isProvider) {
        // out-of-process concurrency control
        final int maxAttempts = LayerMetadataStore.METADATA_MAX_RW_ATTEMPTS;
        long lastModified = metadataFile.lastModified();
        // local-process concurrency control
        final ReadWriteLock lock = getLock(metadataFile);
        lock.readLock().lock();
        try {
            for (int attempts = 0; metadataFile.exists() && attempts < maxAttempts; attempts++) {
                try (InputStream in = isProvider.apply(metadataFile)) {
                    Properties props = new Properties();
                    props.load(in);
                    long currentModDate = metadataFile.lastModified();
                    if (lastModified == currentModDate) {
                        return props;
                    }
                    // Try again since some other GWC updated the file
                    log.debug("Reattempting to read metadata file since timestamp changed");
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return new Properties();
    }

    private InputStream openCompressed(File file) {
        try {
            return new GZIPInputStream(open(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InputStream open(File file) {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Properties loadLayerMetadata(final File metadataFile) {
        return loadLayerMetadata(metadataFile, this::openCompressed);
    }

    private Properties loadLayerMetadata(final String layerName) {
        final File metadataFile = resolveMetadataFile(layerName);
        return this.loadLayerMetadata(metadataFile);
    }

    private String getMetadataFilename() {
        return getLegacyMetadataFilename() + LayerMetadataStore.METADATA_GZIP_EXTENSION;
    }

    private String getLegacyMetadataFilename() {
        return "metadata.properties";
    }

    /**
     * Returns the File related to {@code metadata.properties.gz}, upgrading the legacy {@code
     * metadata.properties} if needed
     *
     * @param layerName
     * @return metadata file (compressed or not, depending if it's present uncompressed)
     */
    private File resolveMetadataFile(final String layerName) {
        final File layerPath = getLayerPath(layerName);
        File metadataFile = new File(layerPath, getMetadataFilename());
        if (!metadataFile.exists()) {
            metadataFile = tryUpgradeLegacyMetadataFile(layerPath, metadataFile);
        }
        return metadataFile;
    }

    // called while holding a write lock on newMetadataFile
    private File tryUpgradeLegacyMetadataFile(File layerPath, File newMetadataFile) {
        final File oldMetadataFile = new File(layerPath, getLegacyMetadataFilename());
        if (newMetadataFile.equals(oldMetadataFile)) throw new IllegalArgumentException();

        if (!oldMetadataFile.exists()) {
            return newMetadataFile;
        }

        final ReadWriteLock newFileLock = getLock(newMetadataFile);
        final ReadWriteLock oldFileLock = getLock(oldMetadataFile);
        newFileLock.writeLock().lock();
        try {
            oldFileLock.writeLock().lock();
            try {
                if (!oldMetadataFile.exists()) {
                    return newMetadataFile;
                }
                log.info("Upgrading legacy layer medatada file " + oldMetadataFile);
                Properties oldProperties = this.getUncompressedLayerMetadata(oldMetadataFile);
                File compressedNewFile = this.writeMetadataFile(oldProperties, newMetadataFile);
                // remove the older format
                oldMetadataFile.delete();
                return compressedNewFile;
            } catch (IOException e) {
                log.error(
                        "Upgrading metadata.properties - Failure creating new compressed file or deleting uncompressed one "
                                + newMetadataFile.getPath()
                                + '-'
                                + e.getMessage());
                throw new UncheckedIOException(e);
            } finally {
                oldFileLock.writeLock().unlock();
            }
        } finally {
            newFileLock.writeLock().unlock();
        }
    }
}
