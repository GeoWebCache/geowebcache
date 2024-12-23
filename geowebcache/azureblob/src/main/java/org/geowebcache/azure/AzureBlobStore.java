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
 * @author Andrea Aime, GeoSolutions, Copyright 2019
 */
package org.geowebcache.azure;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.isNull;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.models.BlobDownloadContentResponse;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.geotools.util.logging.Logging;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.CompositeBlobStore;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.TileRangeIterator;
import org.geowebcache.util.TMSKeyBuilder;
import org.springframework.http.HttpStatus;

public class AzureBlobStore implements BlobStore {

    static Logger log = Logging.getLogger(AzureBlobStore.class.getName());

    private final TMSKeyBuilder keyBuilder;
    private final BlobStoreListenerList listeners = new BlobStoreListenerList();
    private final AzureClient client;
    DeleteManager deleteManager;

    private volatile boolean shutDown = false;

    public AzureBlobStore(AzureBlobStoreData configuration, TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {
        this.client = new AzureClient(configuration);

        String prefix = Optional.ofNullable(configuration.getPrefix()).orElse("");
        this.keyBuilder = new TMSKeyBuilder(prefix, layers);

        // check target is suitable for a cache
        boolean emptyFolder = !client.prefixExists(prefix);
        boolean existingMetadata = client.blobExists(keyBuilder.storeMetadata());

        CompositeBlobStore.checkSuitability(configuration.getLocation(), existingMetadata, emptyFolder);

        // Just a marker to indicate this is a GWC cache.
        client.putProperties(keyBuilder.storeMetadata(), new Properties());

        // deletes are a complicated beast, we have a dedicated class to run them
        deleteManager = new DeleteManager(client, lockProvider, keyBuilder, configuration.getMaxConnections());
        deleteManager.issuePendingBulkDeletes();
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        checkNotNull(layerName, "layerName");

        final String metadataKey = keyBuilder.layerMetadata(layerName);
        final String layerPrefix = keyBuilder.forLayer(layerName);

        // this might not be there, tolerant delete
        if (!client.deleteBlob(metadataKey)) {
            return false;
        }

        boolean layerExists = deleteManager.scheduleAsyncDelete(layerPrefix);
        if (layerExists) {
            listeners.sendLayerDeleted(layerName);
        }
        return layerExists;
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId) throws StorageException {
        checkNotNull(layerName, "layerName");
        checkNotNull(parametersId, "parametersId");

        boolean prefixExists = keyBuilder.forParameters(layerName, parametersId).stream()
                .map(prefix -> {
                    try {
                        return deleteManager.scheduleAsyncDelete(prefix);
                    } catch (StorageException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .reduce(Boolean::logicalOr) // Don't use Stream.anyMatch as it would short
                // circuit
                .orElse(false);
        if (prefixExists) {
            listeners.sendParametersDeleted(layerName, parametersId);
        }
        return prefixExists;
    }

    @Override
    public boolean deleteByGridsetId(final String layerName, final String gridSetId) throws StorageException {

        checkNotNull(layerName, "layerName");
        checkNotNull(gridSetId, "gridSetId");

        final String gridsetPrefix = keyBuilder.forGridset(layerName, gridSetId);

        boolean prefixExists = deleteManager.scheduleAsyncDelete(gridsetPrefix);
        if (prefixExists) {
            listeners.sendGridSubsetDeleted(layerName, gridSetId);
        }
        return prefixExists;
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        final String key = keyBuilder.forTile(obj);
        BlockBlobClient blob = client.getBlockBlobClient(key);

        // don't bother for the extra call if there are no listeners
        if (listeners.isEmpty()) {
            try {
                return blob.deleteIfExists();
            } catch (RuntimeException e) {
                throw new StorageException("Failed to delete tile ", e);
            }
        }

        // if there are listeners, gather extra information
        long oldSize = 0;
        try {
            BlobProperties properties = blob.getProperties();
            oldSize = properties.getBlobSize();
        } catch (BlobStorageException e) {
            if (HttpStatus.NOT_FOUND.value() != e.getStatusCode()) {
                throw new StorageException("Failed to check if the container exists", e);
            }
        }

        try {
            boolean deleted = blob.deleteIfExists();
            if (deleted && oldSize > 0L) {
                obj.setBlobSize((int) oldSize);
                listeners.sendTileDeleted(obj);
            }
            return deleted;
        } catch (RuntimeException e) {
            throw new StorageException("Failed to delete tile ", e);
        }
    }

    @Override
    public boolean delete(TileRange tileRange) throws StorageException {
        // see if there is anything to delete in that range by computing a prefix
        final String coordsPrefix = keyBuilder.coordinatesPrefix(tileRange, false);
        if (!client.prefixExists(coordsPrefix)) {
            return false;
        }

        // open an iterator oer tile locations, to avoid memory accumulation
        final Iterator<long[]> tileLocations = new AbstractIterator<>() {

            // TileRange iterator with 1x1 meta tiling factor
            private TileRangeIterator trIter = new TileRangeIterator(tileRange, new int[] {1, 1});

            @Override
            protected long[] computeNext() {
                long[] gridLoc = trIter.nextMetaGridLocation(new long[3]);
                return gridLoc == null ? endOfData() : gridLoc;
            }
        };

        // if no listeners, we don't need to gather extra tile info, use a dedicated fast path
        if (listeners.isEmpty()) {
            // if there are no listeners, don't bother requesting every tile
            // metadata to notify the listeners
            Iterator<String> keysIterator = Iterators.transform(
                    tileLocations, tl -> keyBuilder.forLocation(coordsPrefix, tl, tileRange.getMimeType()));
            // split the iteration in parts to avoid memory accumulation
            Iterator<List<String>> partition = Iterators.partition(keysIterator, DeleteManager.PAGE_SIZE);

            while (partition.hasNext() && !shutDown) {
                List<String> locations = partition.next();
                deleteManager.deleteParallel(locations);
            }

        } else {
            // if we need to gather info, we'll end up just calling "delete" on each tile
            // this is run here instead of inside the delete manager as we need high level info
            // about tiles, e.g., TileObject, to inform the listeners
            String layerName = tileRange.getLayerName();
            String gridSetId = tileRange.getGridSetId();
            String format = tileRange.getMimeType().getFormat();
            Map<String, String> parameters = tileRange.getParameters();

            Iterator<Callable<?>> tilesIterator = Iterators.transform(tileLocations, xyz -> {
                TileObject tile = TileObject.createQueryTileObject(layerName, xyz, gridSetId, format, parameters);
                tile.setParametersId(tileRange.getParametersId());
                return (Callable<Object>) () -> delete(tile);
            });
            Iterator<List<Callable<?>>> partition = Iterators.partition(tilesIterator, DeleteManager.PAGE_SIZE);

            // once a page of callables is ready, run them in parallel on the delete manager
            while (partition.hasNext() && !shutDown) {
                deleteManager.executeParallel(partition.next());
            }
        }

        return true;
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        final String key = keyBuilder.forTile(obj);
        boolean found;
        try {
            BlobDownloadContentResponse response = client.download(key);
            if (null == response) {
                obj.setBlob(null);
                obj.setBlobSize(0);
                found = false;
            } else {
                BinaryData data = response.getValue();
                OffsetDateTime lastModified = response.getDeserializedHeaders().getLastModified();
                byte[] bytes = data.toBytes();
                obj.setBlobSize(bytes.length);
                obj.setBlob(new ByteArrayResource(bytes));
                obj.setCreated(lastModified.toEpochSecond() * 1000l);
                found = true;
            }
        } catch (BlobStorageException e) {
            throw new StorageException("Error getting " + key, e);
        }
        return found;
    }

    @Override
    public void put(TileObject obj) throws StorageException {
        final Resource blob = obj.getBlob();
        checkNotNull(blob);
        checkNotNull(obj.getBlobFormat());

        final String key = keyBuilder.forTile(obj);

        BlockBlobClient blobURL = client.getBlockBlobClient(key);

        // if there are listeners, gather first the old size with a "head" request
        long oldSize = 0L;
        boolean existed = false;
        if (!listeners.isEmpty()) {
            try {
                BlobProperties properties = blobURL.getProperties();
                oldSize = properties.getBlobSize();
                existed = true;
            } catch (BlobStorageException e) {
                if (HttpStatus.NOT_FOUND.value() != e.getStatusCode()) {
                    throw new StorageException("Failed to check if the container exists", e);
                }
            }
        }

        // then upload
        String mimeType = getMimeType(obj);
        try (InputStream is = blob.getInputStream()) {
            Long length = blob.getSize();
            BinaryData data = BinaryData.fromStream(is, length);
            client.upload(key, data, mimeType);
        } catch (StorageException e) {
            throw new StorageException(
                    "Failed to upload tile to Azure on container " + client.getContainerName() + " and key " + key, e);
        } catch (IOException e) {
            throw new StorageException("Error obtaining date from TileObject " + obj);
        }

        // along with the metadata
        putParametersMetadata(obj.getLayerName(), obj.getParametersId(), obj.getParameters());

        // This is important because listeners may be tracking tile existence
        if (!listeners.isEmpty()) {
            if (existed) {
                listeners.sendTileUpdated(obj, oldSize);
            } else {
                listeners.sendTileStored(obj);
            }
        }
    }

    private String getMimeType(TileObject obj) {
        String mimeType;
        try {
            mimeType = MimeType.createFromFormat(obj.getBlobFormat()).getMimeType();
        } catch (MimeException e) {
            throw new IllegalArgumentException(e);
        }
        return mimeType;
    }

    private void putParametersMetadata(String layerName, String parametersId, Map<String, String> parameters) {
        assert (isNull(parametersId) == isNull(parameters));
        if (isNull(parametersId)) {
            return;
        }
        Properties properties = new Properties();
        parameters.forEach(properties::setProperty);
        String resourceKey = keyBuilder.parametersMetadata(layerName, parametersId);
        try {
            client.putProperties(resourceKey, properties);
        } catch (StorageException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void clear() throws StorageException {
        // mimicking the S3 store here. The parent class javadoc says it should only be used for
        // testing anyways
        throw new UnsupportedOperationException("clear() should not be called");
    }

    @Override
    public void destroy() {
        shutDown = true;
        if (deleteManager != null) {
            deleteManager.close();
        }
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
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        // revisit: this seems to hold true only for GeoServerTileLayer, "standalone" TileLayers
        // return getName() from getId(), as in AbstractTileLayer. Unfortunately the only option
        // for non-GeoServerTileLayers would be copy and delete. Expensive.
        log.fine("No need to rename layers, AzureBlobStore uses layer id as key root");
        if (client.prefixExists(oldLayerName)) {
            listeners.sendLayerRenamed(oldLayerName, newLayerName);
        }
        return true;
    }

    @Nullable
    @Override
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
            client.putProperties(resourceKey, properties);
        } catch (StorageException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Properties getLayerMetadata(String layerName) {
        String key = keyBuilder.layerMetadata(layerName);
        return client.getProperties(key);
    }

    @Override
    public boolean layerExists(String layerName) {
        final String layerPrefix = keyBuilder.forLayer(layerName);
        return client.prefixExists(layerPrefix);
    }

    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        // going big, retrieve all items, since at the end everything must be held in memory anyways
        String parametersMetadataPrefix = keyBuilder.parametersMetadataPrefix(layerName);
        Stream<BlobItem> items = client.listBlobs(parametersMetadataPrefix);

        return items.map(BlobItem::getName)
                .map(this::loadProperties)
                .collect(Collectors.toMap(ParametersUtils::getId, Optional::ofNullable));
    }

    private Map<String, String> loadProperties(String blobKey) {
        Properties properties = client.getProperties(blobKey);
        return properties.entrySet().stream()
                .collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
    }
}
