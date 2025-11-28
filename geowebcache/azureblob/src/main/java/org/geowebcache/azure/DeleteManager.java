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

import com.azure.core.http.rest.PagedResponse;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.batch.BlobBatchClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.DeleteSnapshotsOptionType;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.Closeable;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.TMSKeyBuilder;

/**
 * Class handling deletes, which are normally handled in an asynchronous way in all other stores as well. These are bulk
 * operations like deleting an entire layer, or a rangeset, with potentially million of tiles involved.
 *
 * <p>Using {@link BlobBatchClient}, tile URLs are partitioned into batches (max 256 per batch) to issue bulk delete
 * requests, reducing network overhead, while keeping memory usage low.
 */
class DeleteManager implements Closeable {

    private static final Logger LOG = Logging.getLogger(AzureBlobStore.class.getName());

    /**
     * To manage blobs in batch through {@link BlobBatchClient}, we are limited to 256 blobs per request.
     *
     * @see <a
     *     href="https://learn.microsoft.com/en-us/rest/api/storageservices/blob-batch?utm_source=chatgpt.com&tabs=microsoft-entra-id#request-body">Azure
     *     Blob Batch REST API</a>
     */
    static final int PAGE_SIZE = 256;

    private final TMSKeyBuilder keyBuilder;
    private final AzureClient client;
    private final LockProvider locks;
    private final int concurrency;
    private final ExecutorService deleteExecutor;
    private final Map<String, Long> pendingDeletesKeyTime = new ConcurrentHashMap<>();

    public DeleteManager(AzureClient client, LockProvider locks, TMSKeyBuilder keyBuilder, int maxConnections) {
        this.keyBuilder = keyBuilder;
        this.client = client;
        this.locks = locks;
        this.concurrency = maxConnections;
        this.deleteExecutor = createDeleteExecutorService(client.getContainerName(), maxConnections);
    }

    private static ExecutorService createDeleteExecutorService(String containerName, int parallelism) {
        ThreadFactory tf = new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("GWC AzureBlobStore bulk delete thread-%d. Container: " + containerName)
                .setPriority(Thread.MIN_PRIORITY)
                .build();
        return Executors.newFixedThreadPool(parallelism, tf);
    }

    // Azure, like S3, truncates timestamps to seconds precision and does not allow
    // to programmatically set the last modified time
    private long currentTimeSeconds() {
        return Instant.now().getEpochSecond();
    }

    /** Executes the provided iterator of callables on the delete executor, returning their results */
    public void executeParallel(List<Callable<?>> callables) throws StorageException {
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<?> callable : callables) {
            futures.add(deleteExecutor.submit(callable));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                throw new StorageException("Failed to execute parallel delete", e);
            }
        }
    }

    /** Executes the removal of the specified keys in a parallel fashion, returning the number of removed keys */
    public Long deleteParallel(List<String> keys) throws StorageException {
        try {
            return new KeysBulkDelete(keys).call();
        } catch (Exception e) {
            throw new StorageException("Failed to submit parallel keys execution", e);
        }
    }

    public boolean scheduleAsyncDelete(final String prefix) throws StorageException {
        final long timestamp = currentTimeSeconds();
        String msg = "Issuing bulk delete on '%s/%s' for objects older than %d"
                .formatted(client.getContainerName(), prefix, timestamp);
        LOG.info(msg);

        try {
            Lock lock = locks.getLock(prefix);
            try {
                boolean taskRuns = asyncDelete(prefix, timestamp);
                if (taskRuns) {
                    final String pendingDeletesKey = keyBuilder.pendingDeletes();
                    Properties deletes = client.getProperties(pendingDeletesKey);
                    deletes.setProperty(prefix, String.valueOf(timestamp));
                    client.putProperties(pendingDeletesKey, deletes);
                }
                return taskRuns;
            } finally {
                lock.release();
            }
        } catch (GeoWebCacheException e) {
            throw new StorageException("Failed to schedule asynch delete ", e);
        }
    }

    @SuppressWarnings("Finally")
    public void issuePendingBulkDeletes() throws StorageException {
        final String pendingDeletesKey = keyBuilder.pendingDeletes();
        Lock lock;
        try {
            lock = locks.getLock(pendingDeletesKey);
        } catch (GeoWebCacheException e) {
            throw new StorageException("Unable to lock pending deletes", e);
        }

        try {
            Properties deletes = client.getProperties(pendingDeletesKey);
            Set<String> deletesToClear = new HashSet<>();
            for (Map.Entry<Object, Object> e : deletes.entrySet()) {
                final String prefix = e.getKey().toString();
                final long timestamp = Long.parseLong(e.getValue().toString());
                if (LOG.isLoggable(Level.INFO))
                    LOG.info("Restarting pending bulk delete on '%s/%s':%d"
                            .formatted(client.getContainerName(), prefix, timestamp));
                if (!asyncDelete(prefix, timestamp)) {
                    deletesToClear.add(prefix);
                }
            }
            if (!deletesToClear.isEmpty()) {
                deletes.keySet().removeAll(deletesToClear);
                client.putProperties(pendingDeletesKey, deletes);
            }
        } finally {
            try {
                lock.release();
            } catch (GeoWebCacheException e) {
                throw new StorageException("Unable to unlock pending deletes", e);
            }
        }
    }

    public synchronized boolean asyncDelete(String prefix, long timestamp) {
        // do we have anything to delete?
        if (!client.prefixExists(prefix)) {
            return false;
        }

        // is there any task already deleting a larger set of times in the same prefix
        // folder?
        Long currentTaskTime = pendingDeletesKeyTime.get(prefix);
        if (currentTaskTime != null && currentTaskTime > timestamp) {
            return false;
        }

        PrefixTimeBulkDelete task = new PrefixTimeBulkDelete(prefix, timestamp);
        deleteExecutor.submit(task);
        pendingDeletesKeyTime.put(prefix, timestamp);

        return true;
    }

    @Override
    public void close() {
        deleteExecutor.shutdownNow();
    }

    public class PrefixTimeBulkDelete implements Callable<Long> {
        private final String prefix;
        private final long timestamp;

        public PrefixTimeBulkDelete(String prefix, long timestamp) {
            this.prefix = prefix;
            this.timestamp = timestamp;
        }

        @Override
        public Long call() throws Exception {
            long count = 0L;
            try {
                checkInterrupted();
                if (LOG.isLoggable(Level.INFO))
                    LOG.info("Running bulk delete on '%s/%s':%d"
                            .formatted(client.getContainerName(), prefix, timestamp));

                BlobContainerClient container = client.getContainer();

                int jobPageSize = Math.max(concurrency, PAGE_SIZE);
                ListBlobsOptions options =
                        new ListBlobsOptions().setPrefix(prefix).setMaxResultsPerPage(jobPageSize);
                Iterable<PagedResponse<BlobItem>> response =
                        container.listBlobs(options, null).iterableByPage();

                BlobBatchClient batch = client.getBatch();

                for (PagedResponse<BlobItem> segment : response) {
                    try (PagedResponse<BlobItem> s = segment) { // try-with-resources to please PMD
                        checkInterrupted();

                        List<String> items = s.getValue().stream()
                                .filter(this::equalOrAfter)
                                .map(BlobItem::getName)
                                .collect(Collectors.toList());

                        count += deleteItems(container, batch, items);
                    }
                }
            } catch (InterruptedException | IllegalStateException e) {
                LOG.info("Azure bulk delete aborted for '%s/%s'. Will resume on next startup."
                        .formatted(client.getContainerName(), prefix));
                throw e;
            } catch (RuntimeException e) {
                LOG.log(
                        Level.WARNING,
                        "Unknown error performing bulk Azure blobs delete of '%s/%s'"
                                .formatted(client.getContainerName(), prefix),
                        e);
                throw e;
            }

            if (LOG.isLoggable(Level.INFO))
                LOG.info("Finished bulk delete on '%s/%s':%d. %d objects deleted"
                        .formatted(client.getContainerName(), prefix, timestamp, count));

            clearPendingBulkDelete(prefix, timestamp);
            return count;
        }

        private boolean equalOrAfter(BlobItem blobItem) {
            OffsetDateTime lastModified = blobItem.getProperties().getLastModified();
            long lastModifiedSecs = lastModified.toEpochSecond();
            return timestamp >= lastModifiedSecs;
        }

        private void clearPendingBulkDelete(final String prefix, final long timestamp) throws GeoWebCacheException {
            Long taskTime = pendingDeletesKeyTime.get(prefix);
            if (taskTime == null) {
                return; // someone else cleared it up for us. A task that run after this one but
                // finished before?
            }
            if (taskTime > timestamp) {
                return; // someone else issued a bulk delete after this one for the same key prefix
            }
            final String pendingDeletesKey = keyBuilder.pendingDeletes();
            final Lock lock = locks.getLock(pendingDeletesKey);

            try {
                Properties deletes = client.getProperties(pendingDeletesKey);
                String storedVal = (String) deletes.remove(prefix);
                long storedTimestamp = storedVal == null ? Long.MIN_VALUE : Long.parseLong(storedVal);
                if (timestamp >= storedTimestamp) {
                    client.putProperties(pendingDeletesKey, deletes);
                } else if (LOG.isLoggable(Level.INFO)) {
                    LOG.info("bulk delete finished but there's a newer one ongoing for container '%s/%s'"
                            .formatted(client.getContainerName(), prefix));
                }
            } catch (StorageException e) {
                throw new UncheckedIOException(e);
            } finally {
                lock.release();
            }
        }
    }

    public class KeysBulkDelete implements Callable<Long> {

        private final List<String> keys;

        public KeysBulkDelete(List<String> keys) {
            this.keys = keys;
        }

        @Override
        public Long call() throws Exception {
            long count = 0L;
            try {
                checkInterrupted();
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.finer("Running delete delete on list of items on '%s':%s ... (only the first 100 items listed)"
                            .formatted(client.getContainerName(), keys.subList(0, Math.min(keys.size(), 100))));
                }

                BlobContainerClient container = client.getContainer();
                BlobBatchClient batch = client.getBatch();

                for (int i = 0; i < keys.size(); i += PAGE_SIZE) {
                    count += deleteItems(container, batch, keys.subList(i, Math.min(i + PAGE_SIZE, keys.size())));
                }

            } catch (InterruptedException | IllegalStateException e) {
                LOG.log(Level.INFO, "Azure bulk delete aborted", e);
                throw e;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Unknown error performing bulk Azure delete", e);
                throw e;
            }

            if (LOG.isLoggable(Level.INFO))
                LOG.info("Finished bulk delete on %s, %d objects deleted".formatted(client.getContainerName(), count));
            return count;
        }
    }

    private long deleteItems(BlobContainerClient container, BlobBatchClient batch, List<String> itemNames) {
        List<String> blobsUrls = itemNames.stream()
                .map(n -> container.getBlobClient(n).getBlobUrl())
                .collect(Collectors.toList());

        return batch.deleteBlobs(blobsUrls, DeleteSnapshotsOptionType.INCLUDE).stream()
                .count();
    }

    void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }
}
