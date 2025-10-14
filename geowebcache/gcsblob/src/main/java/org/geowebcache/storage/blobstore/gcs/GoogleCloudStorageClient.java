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

/**
 * A low-level client to interact with a Google Cloud Storage bucket, tailored for {@link GoogleCloudStorageBlobStore}'s
 * needs.
 *
 * <p>This client handles the communication with the GCS API, including authentication, and performs bulk delete
 * operations asynchronously in a background thread pool.
 *
 * @since 1.28
 */
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

    /**
     * Creates a builder initialized with configuration values from the provided blob store info.
     *
     * <p>This factory method extracts configuration parameters from {@link GoogleCloudStorageBlobStoreInfo}, resolving
     * environment variables if enabled through the {@link GeoWebCacheEnvironment}.
     *
     * @param config The Google Cloud Storage blob store configuration.
     * @param environment The GeoWebCache environment for resolving configuration placeholders.
     * @return A builder instance configured with the resolved parameters.
     * @throws org.geowebcache.storage.StorageException if the bucket parameter is not provided or resolution fails.
     */
    public static GoogleCloudStorageClient.Builder builder(
            GoogleCloudStorageBlobStoreInfo config, GeoWebCacheEnvironment environment)
            throws org.geowebcache.storage.StorageException {

        requireNonNull(config);
        requireNonNull(environment);

        GoogleCloudStorageClient.Builder builder = GoogleCloudStorageClient.builder();
        builder.bucket(environment
                .resolveValueIfEnabled(config.getBucket(), String.class)
                .orElseThrow(() -> new IllegalArgumentException("parameter bucket is mandatory")));

        builder.prefix(environment
                .resolveValueIfEnabled(config.getPrefix(), String.class)
                .orElse(null));

        builder.projectId(environment
                .resolveValueIfEnabled(config.getProjectId(), String.class)
                .orElse(null));

        builder.quotaProjectId(environment
                .resolveValueIfEnabled(config.getQuotaProjectId(), String.class)
                .orElse(null));

        builder.endpointUrl(environment
                .resolveValueIfEnabled(config.getEndpointUrl(), String.class)
                .orElse(null));

        builder.apiKey(environment
                .resolveValueIfEnabled(config.getApiKey(), String.class)
                .orElse(null));

        builder.useDefaultCredentialsChain(config.getUseDefaultCredentialsChain());

        return builder;
    }

    /**
     * Creates a new builder for configuring a {@link GoogleCloudStorageClient}.
     *
     * @return A new builder instance with default values.
     */
    public static GoogleCloudStorageClient.Builder builder() {
        return new Builder();
    }

    static class Builder {
        String projectId;
        String quotaProjectId;
        String bucket;
        String prefix;
        String endpointUrl;
        String apiKey;
        boolean useDefaultCredentialsChain;

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder quotaProjectId(String quotaProjectId) {
            this.quotaProjectId = quotaProjectId;
            return this;
        }

        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder endpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder useDefaultCredentialsChain(boolean useDefaultCredentialsChain) {
            this.useDefaultCredentialsChain = useDefaultCredentialsChain;
            return this;
        }

        public GoogleCloudStorageClient build() throws org.geowebcache.storage.StorageException {
            try {
                StorageOptions.Builder builder = StorageOptions.getDefaultInstance().toBuilder();

                if (projectId != null) {
                    builder.setProjectId(projectId);
                }
                if (quotaProjectId != null) {
                    builder.setQuotaProjectId(quotaProjectId);
                }
                if (endpointUrl != null) {
                    // Set custom endpoint for emulators or non-standard GCS endpoints
                    builder.setHost(endpointUrl);
                }
                Credentials credentials = null;
                if (apiKey != null) {
                    credentials = ApiKeyCredentials.create(apiKey);
                } else if (useDefaultCredentialsChain) {
                    try {
                        credentials = GoogleCredentials.getApplicationDefault();
                    } catch (IOException e) {
                        throw new org.geowebcache.storage.StorageException(
                                "Error obtaining default credentials: " + e.getMessage(), e);
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
            } catch (StorageException gcse) {
                throw new org.geowebcache.storage.StorageException(
                        "Error creating GCS client: " + gcse.getMessage(), gcse);
            }
        }
    }

    /**
     * Gets the name of the Google Cloud Storage bucket this client operates on.
     *
     * @return The bucket name.
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * Gets the prefix prepended to all blob paths in the bucket.
     *
     * @return The prefix string, or {@code null} if no prefix is configured.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Gets the full Google Cloud Storage location in {@code gs://} URI format.
     *
     * @return The location as {@code gs://<bucket>/<prefix>}.
     */
    public String getLocation() {
        return "gs://%s/%s".formatted(bucket, prefix);
    }

    /**
     * Checks if a blob with the given key exists in the bucket.
     *
     * @param key The blob key to check.
     * @return {@code true} if the blob exists, {@code false} otherwise.
     * @throws org.geowebcache.storage.StorageException if an error occurs accessing the storage.
     */
    public boolean blobExists(String key) throws org.geowebcache.storage.StorageException {
        return get(key).isPresent();
    }

    /**
     * Lists all blobs whose names begin with the specified prefix.
     *
     * @param prefix The prefix to filter blobs by.
     * @return A stream of matching {@link Blob} objects.
     */
    public Stream<Blob> list(String prefix) throws org.geowebcache.storage.StorageException {
        try {
            return storage.list(bucket, BlobListOption.prefix(requireNonNull(prefix)))
                    .streamAll();
        } catch (StorageException gcse) {
            throw new org.geowebcache.storage.StorageException(gcse.getMessage(), gcse);
        }
    }

    /**
     * Checks if a directory (path prefix) exists in the bucket.
     *
     * <p>This method checks if there are any blobs whose names start with the given path (with a trailing slash added
     * if not present). It only fetches one blob to efficiently determine existence.
     *
     * @param path The directory path to check.
     * @return {@code true} if at least one blob exists with this path prefix, {@code false} otherwise.
     */
    public boolean directoryExists(final String path) throws org.geowebcache.storage.StorageException {
        requireNonNull(path);
        String dirPrefix = dirPrefix(path);
        Page<Blob> blobs;
        try {
            blobs = storage.list(bucket, BlobListOption.prefix(dirPrefix), BlobListOption.pageSize(1));
        } catch (StorageException gcse) {
            throw new org.geowebcache.storage.StorageException(gcse.getMessage(), gcse);
        }
        Iterator<Blob> iterator = blobs.getValues().iterator();
        boolean hasNext = iterator.hasNext();
        if (hasNext) {
            log.fine("Directory exists: " + path);
        } else {
            log.fine("Directory does not exist: " + path);
        }
        return hasNext;
    }

    /**
     * Deletes all blobs under a given path prefix asynchronously.
     *
     * <p>This method checks if the directory exists and, if so, submits a task to a background thread pool to delete
     * all blobs whose names start with the given path. The method returns immediately.
     *
     * <p>The provided {@code path} is treated as a directory prefix. A trailing slash ({@code /}) is added if not
     * already present to ensure only blobs *within* the directory are matched, avoiding accidental deletion of sibling
     * blobs with a similar prefix.
     *
     * @param path The directory path to delete.
     * @return {@code true} if the directory existed and the delete task was submitted, {@code false} if the directory
     *     did not exist.
     */
    public boolean deleteDirectory(String path) throws org.geowebcache.storage.StorageException {
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

    /**
     * Closes this client and shuts down the background delete service.
     *
     * <p>This method initiates an orderly shutdown of the delete thread pool, waiting up to 60 seconds for in-progress
     * deletions to complete. If tasks don't complete within that time, a forced shutdown is attempted. Any ongoing or
     * queued delete operations after this point will be cancelled.
     *
     * <p>This method should be called when the blob store is being destroyed to ensure proper cleanup of resources.
     */
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

    /**
     * Gets the size in bytes of a blob without retrieving its full content.
     *
     * <p>This method performs an optimized request that only fetches the blob's size metadata, making it efficient for
     * checking blob sizes without downloading content.
     *
     * @param key The blob key.
     * @return An {@link OptionalLong} containing the blob size in bytes, or empty if the blob does not exist.
     * @throws org.geowebcache.storage.StorageException if an error occurs accessing the storage.
     */
    public OptionalLong getSize(String key) throws org.geowebcache.storage.StorageException {
        try {
            Blob blob = storage.get(bucket, requireNonNull(key), BlobGetOption.fields(BlobField.SIZE));
            return blob == null ? OptionalLong.empty() : OptionalLong.of(blob.getSize());
        } catch (StorageException e) {
            throw new org.geowebcache.storage.StorageException("Failed to get blob " + key, e);
        }
    }

    /**
     * Retrieves a blob by its key.
     *
     * @param key The blob key.
     * @return An {@link Optional} containing the {@link Blob} if found, or empty if not found.
     * @throws org.geowebcache.storage.StorageException if an error occurs accessing the storage.
     */
    public Optional<Blob> get(String key) throws org.geowebcache.storage.StorageException {
        try {
            return Optional.ofNullable(storage.get(bucket, requireNonNull(key)));
        } catch (StorageException e) {
            throw new org.geowebcache.storage.StorageException("Failed to get blob " + key, e);
        }
    }

    /**
     * Stores a blob in the bucket from a byte array.
     *
     * @param key The blob key (path) under which to store the content.
     * @param bytes The content to store.
     * @param contentType The MIME type of the content (e.g., "image/png").
     * @return The created {@link Blob} object.
     * @throws org.geowebcache.storage.StorageException if the upload fails.
     */
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

    /**
     * Stores a blob in the bucket from a {@link Resource}.
     *
     * <p>This method reads the entire resource into memory before uploading to GCS.
     *
     * @param key The blob key (path) under which to store the content.
     * @param blob The resource containing the content to store.
     * @param contentType The MIME type of the content (e.g., "image/png").
     * @return The created {@link Blob} object.
     * @throws org.geowebcache.storage.StorageException if reading the resource or uploading fails.
     */
    public Blob put(String key, Resource blob, String contentType) throws org.geowebcache.storage.StorageException {
        byte[] bytes;
        try (InputStream is = requireNonNull(blob).getInputStream()) {
            bytes = ByteStreams.toByteArray(is);
        } catch (IOException e) {
            throw new org.geowebcache.storage.StorageException("Failed to upload tile to GCS with key " + key, e);
        }
        return put(key, bytes, contentType);
    }

    /**
     * Deletes a single blob synchronously.
     *
     * @param key The blob key to delete.
     * @return {@code true} if the blob was deleted, {@code false} if it didn't exist.
     * @throws org.geowebcache.storage.StorageException if the delete operation fails.
     */
    public boolean deleteBlob(String key) throws org.geowebcache.storage.StorageException {
        try {
            return storage.delete(bucket, requireNonNull(key));
        } catch (com.google.cloud.storage.StorageException e) {
            throw new org.geowebcache.storage.StorageException("Failed to delete blob " + key, e);
        }
    }

    /**
     * Asynchronously deletes a stream of tiles.
     *
     * @param keys A stream of {@link TileLocation} objects to delete.
     * @throws org.geowebcache.storage.StorageException if an error occurs submitting the delete task.
     */
    public void delete(Stream<TileLocation> keys) throws org.geowebcache.storage.StorageException {
        Stream<BlobId> ids = requireNonNull(keys).map(this::toBlobId);
        try {
            //        deleteInternal(ids);
            deleteService.submit(() -> deleteInternal(ids));
        } catch (com.google.cloud.storage.StorageException gcsException) {
            throw new org.geowebcache.storage.StorageException("Failed to delete tiles", gcsException);
        }
    }

    /**
     * Asynchronously deletes a stream of tiles, invoking a callback for each successfully deleted tile.
     *
     * @param keys A stream of {@link TileLocation} objects to delete.
     * @param callback A {@link BiConsumer} that accepts the {@link TileLocation} and size of each deleted tile.
     */
    public void delete(Stream<TileLocation> keys, BiConsumer<TileLocation, Long> callback) {
        requireNonNull(keys);
        requireNonNull(callback);
        //        deleteInternal(keys, callback);
        deleteService.submit(() -> deleteInternal(keys, callback));
    }

    /**
     * Internal method that performs batched deletions with callbacks for each tile.
     *
     * <p>This method partitions the stream of tiles into batches of up to {@value #DEFAULT_BATCH_SIZE} tiles and
     * processes each batch using the GCS batch API. For each successfully deleted tile, the callback is invoked with
     * the tile location and its size.
     *
     * <p>This method is typically called from a background thread via {@link #delete(Stream, BiConsumer)}.
     *
     * @param keys The stream of tile locations to delete.
     * @param callback The callback invoked for each successfully deleted tile.
     */
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
