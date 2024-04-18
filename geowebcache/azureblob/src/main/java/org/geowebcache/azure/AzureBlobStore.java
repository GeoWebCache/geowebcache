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
 * @author Andrea Aime, GeoSolutions, Copyright 2019
 */
package org.geowebcache.azure;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.isNull;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.microsoft.azure.storage.blob.BlockBlobURL;
import com.microsoft.azure.storage.blob.DownloadResponse;
import com.microsoft.azure.storage.blob.models.BlobGetPropertiesResponse;
import com.microsoft.azure.storage.blob.models.BlobHTTPHeaders;
import com.microsoft.azure.storage.blob.models.BlobItem;
import com.microsoft.rest.v2.RestException;
import com.microsoft.rest.v2.util.FlowableUtil;
import io.reactivex.Flowable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
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

    public AzureBlobStore(
            AzureBlobStoreData configuration, TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {
        this.client = new AzureClient(configuration);

        String prefix = Optional.ofNullable(configuration.getPrefix()).orElse("");
        this.keyBuilder = new TMSKeyBuilder(prefix, layers);

        // check target is suitable for a cache
        boolean emptyFolder = client.listBlobs(prefix, 1).isEmpty();
        boolean existingMetadata = !client.listBlobs(keyBuilder.storeMetadata(), 1).isEmpty();
        CompositeBlobStore.checkSuitability(
                configuration.getLocation(), existingMetadata, emptyFolder);

        // Just a marker to indicate this is a GWC cache.
        client.putProperties(keyBuilder.storeMetadata(), new Properties());

        // deletes are a complicated beast, we have a dedicated class to run them
        deleteManager =
                new DeleteManager(
                        client, lockProvider, keyBuilder, configuration.getMaxConnections());
        deleteManager.issuePendingBulkDeletes();
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        checkNotNull(layerName, "layerName");

        final String metadataKey = keyBuilder.layerMetadata(layerName);
        final String layerPrefix = keyBuilder.forLayer(layerName);

        // this might not be there, tolerant delete
        try {
            BlockBlobURL metadata = client.getBlockBlobURL(metadataKey);
            int statusCode = metadata.delete().blockingGet().statusCode();
            if (!HttpStatus.valueOf(statusCode).is2xxSuccessful()) {
                return false;
            }
        } catch (RestException e) {
            return false;
        }

        boolean layerExists = deleteManager.scheduleAsyncDelete(layerPrefix);
        if (layerExists) {
            listeners.sendLayerDeleted(layerName);
        }
        return layerExists;
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId)
            throws StorageException {
        checkNotNull(layerName, "layerName");
        checkNotNull(parametersId, "parametersId");

        boolean prefixExists =
                keyBuilder.forParameters(layerName, parametersId).stream()
                        .map(
                                prefix -> {
                                    try {
                                        return deleteManager.scheduleAsyncDelete(prefix);
                                    } catch (StorageException e) {
                                        throw new RuntimeException(e);
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
    public boolean deleteByGridsetId(final String layerName, final String gridSetId)
            throws StorageException {

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
        BlockBlobURL blob = client.getBlockBlobURL(key);

        // don't bother for the extra call if there are no listeners
        if (listeners.isEmpty()) {
            try {
                int statusCode = blob.delete().blockingGet().statusCode();
                return HttpStatus.valueOf(statusCode).is2xxSuccessful();
            } catch (RestException e) {
                return false;
            }
        }

        try {
            // if there are listeners, gather extra information
            BlobGetPropertiesResponse properties = blob.getProperties().blockingGet();
            Long oldSize = properties.headers().contentLength();
            int statusCode = blob.delete().blockingGet().statusCode();
            if (!HttpStatus.valueOf(statusCode).is2xxSuccessful()) {
                return false;
            }
            if (oldSize != null) {
                obj.setBlobSize(oldSize.intValue());
            }
        } catch (RestException e) {
            if (e.response().statusCode() != 404) {
                throw new StorageException("Failed to delete tile ", e);
            }
            return false;
        }
        listeners.sendTileDeleted(obj);
        return true;
    }

    @Override
    public boolean delete(TileRange tileRange) throws StorageException {
        // see if there is anything to delete in that range by computing a prefix
        final String coordsPrefix = keyBuilder.coordinatesPrefix(tileRange, false);
        if (client.listBlobs(coordsPrefix, 1).isEmpty()) {
            return false;
        }

        // open an iterator oer tile locations, to avoid memory accumulation
        final Iterator<long[]> tileLocations =
                new AbstractIterator<long[]>() {

                    // TileRange iterator with 1x1 meta tiling factor
                    private TileRangeIterator trIter =
                            new TileRangeIterator(tileRange, new int[] {1, 1});

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
            Iterator<String> keysIterator =
                    Iterators.transform(
                            tileLocations,
                            tl ->
                                    keyBuilder.forLocation(
                                            coordsPrefix, tl, tileRange.getMimeType()));
            // split the iteration in parts to avoid memory accumulation
            Iterator<List<String>> partition =
                    Iterators.partition(keysIterator, DeleteManager.PAGE_SIZE);

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

            Iterator<Callable<?>> tilesIterator =
                    Iterators.transform(
                            tileLocations,
                            xyz -> {
                                TileObject tile =
                                        TileObject.createQueryTileObject(
                                                layerName, xyz, gridSetId, format, parameters);
                                tile.setParametersId(tileRange.getParametersId());
                                return (Callable<Object>) () -> delete(tile);
                            });
            Iterator<List<Callable<?>>> partition =
                    Iterators.partition(tilesIterator, DeleteManager.PAGE_SIZE);

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
        final BlockBlobURL blob = client.getBlockBlobURL(key);
        try {
            DownloadResponse response = blob.download().blockingGet();
            ByteBuffer buffer =
                    FlowableUtil.collectBytesInBuffer(response.body(null)).blockingGet();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            obj.setBlobSize(bytes.length);
            obj.setBlob(new ByteArrayResource(bytes));
            obj.setCreated(response.headers().lastModified().toEpochSecond() * 1000l);
        } catch (RestException e) {
            if (e.response().statusCode() == 404) {
                return false;
            }
            throw new StorageException("Error getting " + key, e);
        }
        return true;
    }

    @Override
    public void put(TileObject obj) throws StorageException {
        final Resource blob = obj.getBlob();
        checkNotNull(blob);
        checkNotNull(obj.getBlobFormat());

        final String key = keyBuilder.forTile(obj);

        BlockBlobURL blobURL = client.getBlockBlobURL(key);

        // if there are listeners, gather first the old size with a "head" request
        Long oldSize = null;
        boolean existed = false;
        if (!listeners.isEmpty()) {
            try {
                BlobGetPropertiesResponse properties = blobURL.getProperties().blockingGet();
                oldSize = properties.headers().contentLength();
                existed = true;
            } catch (RestException e) {
                if (e.response().statusCode() != HttpStatus.NOT_FOUND.value()) {
                    throw new StorageException("Failed to check if the container exists", e);
                }
            }
        }

        // then upload
        try (InputStream is = blob.getInputStream()) {
            byte[] bytes = IOUtils.toByteArray(is);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            String mimeType = MimeType.createFromFormat(obj.getBlobFormat()).getMimeType();
            BlobHTTPHeaders headers = new BlobHTTPHeaders().withBlobContentType(mimeType);
            int status =
                    blobURL.upload(Flowable.just(buffer), bytes.length, headers, null, null, null)
                            .blockingGet()
                            .statusCode();
            if (!HttpStatus.valueOf(status).is2xxSuccessful()) {
                throw new StorageException(
                        "Failed to upload tile to Azure on container "
                                + client.getContainerName()
                                + " and key "
                                + key
                                + " got HTTP  status "
                                + status);
            }
        } catch (RestException | IOException | MimeException e) {
            throw new StorageException(
                    "Failed to upload tile to Azure on container "
                            + client.getContainerName()
                            + " and key "
                            + key,
                    e);
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

    private void putParametersMetadata(
            String layerName, String parametersId, Map<String, String> parameters) {
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
            throw new RuntimeException(e);
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
        if (client != null) {
            client.close();
        }
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
        log.fine("No need to rename layers, AzureBlobStore uses layer id as key root");
        if (client.listBlobs(oldLayerName, 1).size() > 0) {
            listeners.sendLayerRenamed(oldLayerName, newLayerName);
        }
        return true;
    }

    @Nullable
    @Override
    public String getLayerMetadata(String layerName, String key) {
        Properties properties = getLayerMetadata(layerName);
        String value = properties.getProperty(key);
        return value;
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        Properties properties = getLayerMetadata(layerName);
        properties.setProperty(key, value);
        String resourceKey = keyBuilder.layerMetadata(layerName);
        try {
            client.putProperties(resourceKey, properties);
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
    }

    private Properties getLayerMetadata(String layerName) {
        String key = keyBuilder.layerMetadata(layerName);
        try {
            return client.getProperties(key);
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean layerExists(String layerName) {
        final String coordsPrefix = keyBuilder.forLayer(layerName);
        return client.listBlobs(coordsPrefix, 1).size() > 0;
    }

    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        // going big, with MAX_VALUE, since at the end everything must be held in memory anyways
        List<BlobItem> items =
                client.listBlobs(keyBuilder.parametersMetadataPrefix(layerName), Integer.MAX_VALUE);
        Map<String, Optional<Map<String, String>>> result = new HashMap<>();
        try {
            for (BlobItem item : items) {

                Map<String, String> properties =
                        client.getProperties(item.name()).entrySet().stream()
                                .collect(
                                        Collectors.toMap(
                                                e -> (String) e.getKey(),
                                                e -> (String) e.getValue()));
                result.put(ParametersUtils.getId(properties), Optional.of(properties));
            }
            return result;
        } catch (StorageException e) {
            throw new RuntimeException("Failed to retrieve properties mappings", e);
        }
    }
}
