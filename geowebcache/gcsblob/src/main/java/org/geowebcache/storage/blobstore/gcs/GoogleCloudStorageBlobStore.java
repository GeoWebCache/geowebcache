/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan, Camptocamp, Copyright 2025
 */
package org.geowebcache.storage.blobstore.gcs;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.StorageException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.CompositeBlobStore;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.TileRangeIterator;
import org.geowebcache.storage.UnsuitableStorageException;
import org.geowebcache.util.TMSKeyBuilder;

/**
 * A {@link BlobStore} implementation that stores tiles in a Google Cloud Storage bucket.
 *
 * @since 1.28
 */
public class GoogleCloudStorageBlobStore implements BlobStore {

    static Logger log = Logging.getLogger(GoogleCloudStorageBlobStore.class.getName());

    private final TMSKeyBuilder keyBuilder;
    private final BlobStoreListenerList listeners = new BlobStoreListenerList();
    final GoogleCloudStorageClient client;

    private TileLayerDispatcher layers;

    /**
     * @param client a pre-configured {@link GoogleCloudStorageClient}
     * @param layers the tile layer dispatcher to build tile keys from
     * @throws org.geowebcache.storage.UnsuitableStorageException if the target bucket is not suitable for a cache (e.g.
     *     it's not empty and does not contain a {@code metadata.properties} marker file <strong>or</strong> the bucket
     *     can't be accessed, for example due to bad credentials.
     * @implNote {@link UnsuitableStorageException} will be thrown also if the bucket can't be accessed to account fot
     *     {@link XMLConfiguration#addBlobStore} and {@link XMLConfiguration#modifyBlobStore} checking for
     *     {@code instanceof UnsuitableStorageException} to prevent saving a misconfigured blob store. Otherwise the
     *     blobstore would be saved even with an invalid state and prevent application startup later on.
     */
    public GoogleCloudStorageBlobStore(GoogleCloudStorageClient client, TileLayerDispatcher layers)
            throws org.geowebcache.storage.UnsuitableStorageException {

        this.client = requireNonNull(client);
        this.layers = requireNonNull(layers);

        String prefix = Optional.ofNullable(client.getPrefix()).orElse("");
        this.keyBuilder = new TMSKeyBuilder(prefix, layers);

        try {
            ensureCacheSuitability(prefix);
        } catch (UnsuitableStorageException e) {
            throw e;
        } catch (org.geowebcache.storage.StorageException somethingElse) {
            // throw  UnsuitableStorageException instead, which is a subclass of StorageException
            // The GeoServer UI checks for instanceof UnsuitableStorageException when saving a blobstore that failed to
            // be created. Otherwise it'll save it with the invalid configuration.
            UnsuitableStorageException e = new UnsuitableStorageException(somethingElse.getMessage());
            e.addSuppressed(somethingElse);
            throw e;
        }
    }

    void ensureCacheSuitability(String prefix) throws org.geowebcache.storage.StorageException {

        // check target is suitable for a cache
        boolean emptyFolder = !client.directoryExists(prefix);
        boolean existingMetadata = false;

        final String storeMetadataKey = keyBuilder.storeMetadata();
        if (!emptyFolder) {
            existingMetadata = client.blobExists(storeMetadataKey);
        }
        CompositeBlobStore.checkSuitability(client.getLocation(), existingMetadata, emptyFolder);

        // Just a marker to indicate this is a GWC cache.
        if (!existingMetadata) {
            putProperties(storeMetadataKey, new Properties());
        }
    }

    private void putProperties(String key, Properties properties) throws org.geowebcache.storage.StorageException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            properties.store(out, "");
            byte[] bytes = out.toByteArray();
            client.put(key, bytes, "text/plain");
        } catch (IOException | StorageException e) {
            throw new org.geowebcache.storage.StorageException("Failed to write properties to " + key, e);
        }
    }

    /**
     * Retrieves a tile from the blob store.
     *
     * @param obj The {@link TileObject} to populate with tile data.
     * @return {@code true} if the tile was found, {@code false} otherwise.
     * @throws org.geowebcache.storage.StorageException if an error occurs while accessing the blob store.
     */
    @Override
    public boolean get(TileObject obj) throws org.geowebcache.storage.StorageException {
        final String key = keyBuilder.forTile(obj);
        Optional<Blob> blob = client.get(key);
        if (blob.isEmpty()) {
            obj.setBlob(null);
            obj.setBlobSize(0);
            return false;
        }

        Blob found = blob.orElseThrow();
        byte[] bytes = found.getContent();
        obj.setBlobSize(bytes.length);
        obj.setBlob(new ByteArrayResource(bytes));
        OffsetDateTime updateTime = found.getUpdateTimeOffsetDateTime();
        obj.setCreated(updateTime.toInstant().toEpochMilli());
        return true;
    }

    /**
     * Stores a tile in the blob store.
     *
     * @param obj The {@link TileObject} containing the tile data to store.
     * @throws org.geowebcache.storage.StorageException if an error occurs while writing to the blob store.
     */
    @Override
    public void put(TileObject obj) throws org.geowebcache.storage.StorageException {
        final Resource content = checkNotNull(obj).getBlob();
        checkNotNull(content);
        checkNotNull(obj.getBlobFormat());

        final String tileKey = keyBuilder.forTile(obj);
        final String contentType = getMimeType(obj);

        final long oldSize = listeners.isEmpty() ? -1 : client.getSize(tileKey).orElse(-2L);

        client.put(tileKey, content, contentType);

        putParametersMetadata(obj.getLayerName(), obj.getParametersId(), obj.getParameters());

        if (oldSize < 0) {
            listeners.sendTileStored(obj);
        } else {
            listeners.sendTileUpdated(obj, oldSize);
        }
    }

    /**
     * Deletes all tiles for a given layer asynchronously.
     *
     * <p>This method submits the delete operation to a background task and returns immediately.
     *
     * @param layerName The name of the layer to delete.
     * @return {@code true} if the layer existed and the delete task was submitted, {@code false} otherwise.
     * @throws org.geowebcache.storage.StorageException if an error occurs during the delete operation.
     */
    @Override
    public boolean delete(String layerName) throws org.geowebcache.storage.StorageException {
        checkNotNull(layerName, "layerName");

        final String metadataKey = keyBuilder.layerMetadata(layerName);
        final String layerPrefix = keyBuilder.forLayer(layerName);

        // this might not be there, tolerant delete
        client.deleteBlob(metadataKey);

        boolean layerExists = client.deleteDirectory(layerPrefix);
        if (layerExists) {
            listeners.sendLayerDeleted(layerName);
        }
        return layerExists;
    }

    /**
     * Deletes a set of tiles for a layer identified by a parameters ID, asynchronously.
     *
     * <p>This method submits the delete operation to a background task and returns immediately.
     *
     * @param layerName The name of the layer.
     * @param parametersId The ID of the parameter set.
     * @return {@code true} if any tiles were deleted, {@code false} otherwise.
     * @throws org.geowebcache.storage.StorageException if an error occurs.
     */
    @Override
    public boolean deleteByParametersId(String layerName, String parametersId)
            throws org.geowebcache.storage.StorageException {
        checkNotNull(layerName, "layerName");
        checkNotNull(parametersId, "parametersId");

        Set<String> gridsetAndFormatPrefixes = keyBuilder.forParameters(layerName, parametersId);
        // for each <prefix>/<layer>/<gridset>/<format>/<parametersId>/
        boolean prefixExists = false;
        for (String prefix : gridsetAndFormatPrefixes) {
            prefixExists |= client.deleteDirectory(prefix);
        }
        if (prefixExists) {
            listeners.sendParametersDeleted(layerName, parametersId);
        }
        return prefixExists;
    }

    /**
     * Deletes all tiles for a specific gridset within a layer, asynchronously.
     *
     * <p>This method submits the delete operation to a background task and returns immediately.
     *
     * @param layerName The name of the layer.
     * @param gridSetId The ID of the gridset.
     * @return {@code true} if the gridset existed and was deleted, {@code false} otherwise.
     * @throws org.geowebcache.storage.StorageException if an error occurs.
     */
    @Override
    public boolean deleteByGridsetId(final String layerName, final String gridSetId)
            throws org.geowebcache.storage.StorageException {
        checkNotNull(layerName, "layerName");
        checkNotNull(gridSetId, "gridSetId");

        final String gridsetPrefix = keyBuilder.forGridset(layerName, gridSetId);

        boolean prefixExists = client.deleteDirectory(gridsetPrefix);
        if (prefixExists) {
            listeners.sendGridSubsetDeleted(layerName, gridSetId);
        }
        return prefixExists;
    }

    /**
     * Deletes a single tile synchronously.
     *
     * @param obj The {@link TileObject} to delete.
     * @return {@code true} if the tile was deleted, {@code false} if it did not exist.
     * @throws org.geowebcache.storage.StorageException if an error occurs.
     */
    @Override
    public boolean delete(TileObject obj) throws org.geowebcache.storage.StorageException {
        final String tileKey = keyBuilder.forTile(obj);

        // don't bother for the extra call if there are no listeners
        if (listeners.isEmpty()) {
            return client.deleteBlob(tileKey);
        }

        // if there are listeners, gather extra information
        final long oldSize = client.getSize(tileKey).orElse(0L);
        final boolean deleted = client.deleteBlob(tileKey);
        if (deleted && oldSize > 0L) {
            obj.setBlobSize((int) oldSize);
            listeners.sendTileDeleted(obj);
        }
        return deleted;
    }

    /**
     * Deletes a range of tiles asynchronously.
     *
     * <p>This method submits the delete operation to a background task and returns immediately.
     *
     * @param tileRange The range of tiles to delete.
     * @return {@code true} if the tile range existed and the delete task was submitted, {@code false} otherwise.
     * @throws org.geowebcache.storage.StorageException if an error occurs.
     */
    @Override
    public boolean delete(TileRange tileRange) throws org.geowebcache.storage.StorageException {
        requireNonNull(tileRange);
        final boolean endWithSlash = false;
        // the key prefix up to the coordinates (i.e. {@code "<prefix>/<layer>/<gridset>/<format>/<parametersId>"})
        final String coordsPrefix = keyBuilder.coordinatesPrefix(tileRange, endWithSlash);

        if (client.directoryExists(coordsPrefix)) {
            Stream<TileLocation> tiles = toTileLocations(tileRange);
            if (listeners.isEmpty()) {
                client.delete(tiles);
            } else {
                BiConsumer<TileLocation, Long> callback = this::sendTileDeleted;
                client.delete(tiles, callback);
            }
            return true;
        }
        return false;
    }

    void sendTileDeleted(TileLocation tile, long size) {
        String layerName = tile.cache().layerName();
        String gridsetId = tile.cache().gridsetId();
        MimeType mimeType = tile.cache().format();
        String format = mimeType.getFormat();
        String parametersId = tile.cache().parametersId();
        long x = tile.tile().x();
        long y = tile.tile().y();
        int z = tile.tile().z();
        listeners.sendTileDeleted(layerName, gridsetId, format, parametersId, x, y, z, size);
    }

    private Stream<TileLocation> toTileLocations(TileRange tileRange) {
        String layerName = tileRange.getLayerName();
        String layerId;
        // we need the layer name and the layer id, for the most part they seem to be the same, but not in
        // GeoServerTileLayers
        try {
            TileLayer tileLayer = layers.getTileLayer(layerName);
            layerId = tileLayer.getId();
        } catch (GeoWebCacheException e) {
            throw new IllegalStateException(e);
        }

        String gridSetId = tileRange.getGridSetId();
        MimeType mimeType = tileRange.getMimeType();
        String parametersId = tileRange.getParametersId();

        final CacheId tileCacheId = new CacheId(layerId, layerName, gridSetId, mimeType, parametersId);
        Stream<TileIndex> tileIndices = toTileIndices(tileRange);
        String prefix = client.getPrefix();
        return tileIndices.map(ti -> new TileLocation(prefix, tileCacheId, ti));
    }

    static Stream<TileIndex> toTileIndices(TileRange tileRange) {

        final int[] metaTilingFactors = {1, 1};
        final TileRangeIterator trIter = new TileRangeIterator(tileRange, metaTilingFactors);

        // optimization for TileRangeIterator.nextMetaGridLocation() to avoid creating many arrays
        final long[] reusedGridLoc = new long[3];

        Stream<long[]> gridLocations = Stream.generate(() -> trIter.nextMetaGridLocation(reusedGridLoc))
                // Stream.generate() creates an infinite stream, we need to end it on null
                .takeWhile(Objects::nonNull);

        return gridLocations.map(gridLoc -> new TileIndex(gridLoc[0], gridLoc[1], (int) gridLoc[2]));
    }

    private String getMimeType(TileObject obj) {
        try {
            return MimeType.createFromFormat(obj.getBlobFormat()).getMimeType();
        } catch (MimeException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Local cache of existing parameters metadata to avoid every {@link #put(TileObject)} call to store the same
     * properties file.
     *
     * <p>This is ok since the parameters id is a {@link ParametersUtils#getId(Map) hash code} of the contents
     */
    private Map<String, String> existingParametersMedatata = new ConcurrentHashMap<>();

    void putParametersMetadata(String layerName, String parametersId, Map<String, String> parameters) {
        assert (isNull(parametersId) == isNull(parameters));
        if (isNull(parametersId)) {
            return;
        }
        if (existingParametersMedatata.containsKey(parametersId)) {
            return;
        }

        Properties properties = new Properties();
        parameters.forEach(properties::setProperty);
        String resourceKey = keyBuilder.parametersMetadata(layerName, parametersId);
        try {
            putProperties(resourceKey, properties);
            existingParametersMedatata.put(layerName, parametersId);
        } catch (org.geowebcache.storage.StorageException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void clear() throws org.geowebcache.storage.StorageException {
        throw new UnsupportedOperationException("clear() should not be called");
    }

    @Override
    public void destroy() {
        client.close();
    }

    @Override
    public void addListener(BlobStoreListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return listeners.removeListener(listener);
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws org.geowebcache.storage.StorageException {
        log.fine("No need to rename layers, GoogleCloudStorageBlobStore uses layer id as key root");
        if (client.directoryExists(oldLayerName)) {
            listeners.sendLayerRenamed(oldLayerName, newLayerName);
        }
        return true;
    }

    @Override
    @Nullable
    public String getLayerMetadata(String layerName, String key) {
        Properties properties = getLayerMetadata(layerName);
        return properties.getProperty(key);
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        Properties properties = getLayerMetadata(layerName);
        properties.setProperty(key, value);
        String resourceKey = keyBuilder.layerMetadata(layerName);
        try {
            putProperties(resourceKey, properties);
        } catch (org.geowebcache.storage.StorageException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Properties getLayerMetadata(String layerName) {
        String key = keyBuilder.layerMetadata(layerName);
        return findProperties(key).orElseGet(Properties::new);
    }

    @Override
    public boolean layerExists(String layerName) {
        final String layerPrefix = keyBuilder.forLayer(layerName);
        try {
            return client.directoryExists(layerPrefix);
        } catch (org.geowebcache.storage.StorageException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws UncheckedIOException if {@link GoogleCloudStorageClient#list(String)} throws an
     *     {@link org.geowebcache.storage.StorageException}
     */
    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        String parametersMetadataPrefix = keyBuilder.parametersMetadataPrefix(layerName);
        try (Stream<Blob> blobStream = client.list(parametersMetadataPrefix)) {
            return blobStream
                    .map(Blob::getName)
                    .map(this::loadProperties)
                    .collect(Collectors.toMap(ParametersUtils::getId, Optional::ofNullable));
        } catch (org.geowebcache.storage.StorageException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<Properties> findProperties(String key) {
        try {
            return client.get(key).map(this::toProperties);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to read properties from " + key, e);
        }
        return Optional.empty();
    }

    private Map<String, String> loadProperties(String blobKey) {
        return findProperties(blobKey).map(this::toMap).orElse(Map.of());
    }

    private Properties toProperties(Blob blob) {
        try {
            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(blob.getContent()));
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> toMap(Properties properties) {
        return properties.entrySet().stream()
                .collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
    }
}
