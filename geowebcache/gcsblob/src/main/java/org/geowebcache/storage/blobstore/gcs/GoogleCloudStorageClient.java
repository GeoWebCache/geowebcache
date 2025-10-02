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
 */
package org.geowebcache.storage.blobstore.gcs;

import static java.util.Objects.requireNonNull;

import com.google.api.gax.paging.Page;
import com.google.auth.ApiKeyCredentials;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.BatchResult;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobField;
import com.google.cloud.storage.Storage.BlobGetOption;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageBatch;
import com.google.cloud.storage.StorageBatchResult;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.io.Resource;

class GoogleCloudStorageClient {
    static Logger log = Logging.getLogger(GoogleCloudStorageClient.class.getName());

    private static final int DEFAULT_BATCH_SIZE = 1000;
    private final ExecutorService deleteService;

    private final Storage storage;

    private final String bucket;

    /** Prefix prepended to all blob paths */
    private final String prefix;

    private volatile boolean closed;

    private GoogleCloudStorageClient(Storage storageClient, String bucket, String prefix) {
        this.storage = storageClient;
        this.bucket = bucket;
        this.prefix = prefix;
        int poolSize = Runtime.getRuntime().availableProcessors();
        this.deleteService = Executors.newFixedThreadPool(poolSize);
    }

    public static GoogleCloudStorageClient buildClient(
            GoogleCloudStorageBlobStoreInfo config, GeoWebCacheEnvironment environment) throws StorageException {
        requireNonNull(config);
        requireNonNull(environment);

        String projectId = config.getProjectId();
        String quotaProjectId = config.getQuotaProjectId();
        String host = config.getHost();

        String bucket = config.getBucket();
        String prefix = config.getPrefix();
        String apiKey = config.getApiKey();
        boolean defaultCredentialsChain = config.isDefaultCredentialsChain();

        StorageOptions.Builder builder = StorageOptions.getDefaultInstance().toBuilder();
        if (projectId != null) {
            builder.setProjectId(projectId);
        }
        if (quotaProjectId != null) {
            builder.setQuotaProjectId(quotaProjectId);
        }
        if (host != null) {
            // Set custom endpoint for emulators or non-standard GCS endpoints
            builder.setHost(host);
        }
        Credentials credentials = null;
        if (apiKey != null) {
            credentials = ApiKeyCredentials.create(apiKey);
        } else if (defaultCredentialsChain) {
            try {
                credentials = GoogleCredentials.getApplicationDefault();
            } catch (IOException e) {
                throw new StorageException(e);
            }
        }
        if (credentials != null) {
            // credentials need to be set after projectId and quotaProjectId so its setter will
            // check whether projectId is null and get it from credentials if its a ServiceAccountCredentials
            // or quotaProjectId is null and get it from credentials if it's a QuotaProjectIdProvider
            builder.setCredentials(credentials);
        }
        Storage storageClient = builder.build().getService();

        return new GoogleCloudStorageClient(storageClient, bucket, prefix);
    }

    public String getBucket() {
        return bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getLocation() {
        return "gs://%s/%s".formatted(bucket, prefix);
    }

    public boolean blobExists(String key) throws org.geowebcache.storage.StorageException {
        return get(key).isPresent();
    }

    /** Returns blobs whose names begin with this {@code prefix}. */
    public Stream<Blob> list(String prefix) {
        return storage.list(bucket, BlobListOption.prefix(requireNonNull(prefix)))
                .streamAll();
    }

    public boolean directoryExists(final String path) {
        requireNonNull(path);
        String dirPrefix = dirPrefix(path);
        Page<Blob> blobs = storage.list(bucket, BlobListOption.prefix(dirPrefix), BlobListOption.pageSize(1));
        Iterator<Blob> iterator = blobs.getValues().iterator();
        boolean hasNext = iterator.hasNext();
        if (hasNext) {
            log.fine("Directory exists: " + path);
        } else {
            log.fine("Directory does not exist: " + path);
        }
        return hasNext;
    }

    public boolean deleteDirectory(String path) {
        requireNonNull(path);
        if (directoryExists(path)) {
            String dirPrefix = dirPrefix(path);
            deleteService.submit(() -> deleteAllByPrefix(dirPrefix));
            return true;
        }
        return false;
    }

    private String dirPrefix(String path) {
        // Add trailing slash to ensure we only delete blobs within this specific directory
        String dirPrefix = path.isEmpty() || path.endsWith("/") ? path : path + "/";
        return dirPrefix;
    }

    public void close() {
        closed = true;
        if (!deleteService.isShutdown()) {
            deleteService.shutdown();
            try {
                if (!deleteService.awaitTermination(60, TimeUnit.SECONDS)) {
                    deleteService.shutdownNow();
                }
            } catch (InterruptedException e) {
                deleteService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public OptionalLong getSize(String key) throws org.geowebcache.storage.StorageException {
        try {
            Blob blob = storage.get(bucket, requireNonNull(key), BlobGetOption.fields(BlobField.SIZE));
            return blob == null ? OptionalLong.empty() : OptionalLong.of(blob.getSize());
        } catch (StorageException e) {
            throw new org.geowebcache.storage.StorageException("Failed to get blob " + key, e);
        }
    }

    public Optional<Blob> get(String key) throws org.geowebcache.storage.StorageException {
        try {
            return Optional.ofNullable(storage.get(bucket, requireNonNull(key)));
        } catch (StorageException e) {
            throw new org.geowebcache.storage.StorageException("Failed to get blob " + key, e);
        }
    }

    public Blob put(String key, byte[] bytes, String contentType) throws org.geowebcache.storage.StorageException {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucket, requireNonNull(key))
                .setContentType(requireNonNull(contentType))
                .build();
        try {
            return storage.create(blobInfo, bytes);
        } catch (Exception e) {
            throw new org.geowebcache.storage.StorageException("Failed to upload tile to GCS with key " + key, e);
        }
    }

    public Blob put(String key, Resource blob, String contentType) throws org.geowebcache.storage.StorageException {
        byte[] bytes;
        try (InputStream is = requireNonNull(blob).getInputStream()) {
            bytes = ByteStreams.toByteArray(is);
        } catch (IOException e) {
            throw new org.geowebcache.storage.StorageException("Failed to upload tile to GCS with key " + key, e);
        }
        return put(key, bytes, contentType);
    }

    public boolean deleteBlob(String key) throws org.geowebcache.storage.StorageException {
        try {
            return storage.delete(bucket, requireNonNull(key));
        } catch (com.google.cloud.storage.StorageException e) {
            throw new org.geowebcache.storage.StorageException("Failed to delete blob " + key, e);
        }
    }

    public void delete(Stream<TileLocation> keys) throws org.geowebcache.storage.StorageException {
        Stream<BlobId> ids = requireNonNull(keys).map(this::toBlobId);
        try {
            //        deleteInternal(ids);
            deleteService.submit(() -> deleteInternal(ids));
        } catch (com.google.cloud.storage.StorageException gcsException) {
            throw new org.geowebcache.storage.StorageException("Failed to delete tiles", gcsException);
        }
    }

    public void delete(Stream<TileLocation> keys, BiConsumer<TileLocation, Long> callback) {
        requireNonNull(keys);
        requireNonNull(callback);
        //        deleteInternal(keys, callback);
        deleteService.submit(() -> deleteInternal(keys, callback));
    }

    public void deleteInternal(Stream<TileLocation> keys, BiConsumer<TileLocation, Long> callback) {
        Iterator<List<TileLocation>> partitions = Iterators.partition(keys.iterator(), DEFAULT_BATCH_SIZE);

        while (partitions.hasNext() && !closed) {
            deleteInternal(partitions.next(), callback);
        }
    }

    private void deleteInternal(List<TileLocation> partition, BiConsumer<TileLocation, Long> callback) {
        List<BlobId> ids = partition.stream().map(this::toBlobId).toList();

        StorageBatch batch = storage.batch();
        for (int i = 0; i < partition.size(); i++) {
            TileLocation tile = partition.get(i);

            BlobId blobId = ids.get(i);
            StorageBatchResult<Blob> size = batch.get(blobId, BlobGetOption.fields(BlobField.SIZE));
            batch.delete(blobId).notify(new TileDeleteCallback(tile, size, callback, () -> this.closed));
        }
        batch.submit();
    }

    private record TileDeleteCallback(
            TileLocation tile,
            StorageBatchResult<Blob> sizeResult,
            BiConsumer<TileLocation, Long> callback,
            BooleanSupplier closedCheck)
            implements BatchResult.Callback<Boolean, StorageException> {

        @Override
        public void success(Boolean result) {
            if (closedCheck.getAsBoolean()) return;
            if (!result) {
                if (log.isLoggable(Level.FINEST)) {
                    log.finest("Tile didn't exist while deleting " + tile.getStorageKey());
                }
                return;
            }

            if (sizeResult.completed()) {
                Blob sizeOnlyBlob = sizeResult.get();
                Long size = sizeOnlyBlob.getSize();
                callback.accept(tile, size);
            } else {
                throw new IllegalArgumentException("%s size fetch didn't complete".formatted(tile));
            }
        }

        @Override
        public void error(StorageException exception) {
            if (!closedCheck.getAsBoolean()) {
                log.log(Level.FINER, "Exception deleting tile " + tile.getStorageKey(), exception);
            }
        }
    }

    private void deleteAllByPrefix(String prefix) {
        Stream<Blob> prefixedBlobs = storage.list(
                        bucket, BlobListOption.prefix(prefix), BlobListOption.fields(BlobField.SIZE))
                .streamAll();

        Stream<BlobId> blobIds = prefixedBlobs.map(Blob::getBlobId);

        deleteInternal(blobIds);
    }

    private void deleteInternal(Stream<BlobId> blobIds) {
        Iterator<List<BlobId>> partitions = Iterators.partition(blobIds.iterator(), DEFAULT_BATCH_SIZE);

        while (partitions.hasNext() && !closed) {
            List<BlobId> ids = partitions.next();
            storage.delete(ids); // A batch request is used to perform this call
        }
    }

    private BlobId toBlobId(TileLocation loc) {
        String storageKey = loc.getStorageKey();
        return BlobId.of(bucket, storageKey);
    }
}
