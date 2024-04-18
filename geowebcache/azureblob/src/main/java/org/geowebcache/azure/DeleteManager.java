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

import static org.geowebcache.azure.AzureBlobStore.log;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.ListBlobsOptions;
import com.microsoft.azure.storage.blob.models.BlobFlatListSegment;
import com.microsoft.azure.storage.blob.models.BlobItem;
import com.microsoft.azure.storage.blob.models.ContainerListBlobFlatSegmentResponse;
import com.microsoft.rest.v2.RestException;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.TMSKeyBuilder;
import org.springframework.http.HttpStatus;

/**
 * Class handling deletes, which are normally handled in an asynchronous way in all other stores as
 * well. These are bulk operations like deleting an entire layer, or a rangeset, with potentially
 * million of tiles involved.
 *
 * <p>Unfortunately the Azure BLOB API has no concept of bulk delete, and no concept of containment
 * either, so tiles have to be enumerated one by one and a delete issued on each one. This calls for
 * a parallel execution, and requires avoiding accumulation of references to all tiles that need
 * removing in memory, as they could be millions or more, hence code that tries to run over the
 * tiles in pages
 */
class DeleteManager implements Closeable {
    /**
     * the page size here is not about limiting the requests, but ensures that we don't end up using
     * too much memory while processing millions of tiles, that would be otherwise all queued on the
     * {@link ExecutorService}
     */
    static final int PAGE_SIZE = 1000;

    private final TMSKeyBuilder keyBuilder;
    private final AzureClient client;
    private final LockProvider locks;
    private final int concurrency;
    private ExecutorService deleteExecutor;
    private Map<String, Long> pendingDeletesKeyTime = new ConcurrentHashMap<>();

    public DeleteManager(
            AzureClient client, LockProvider locks, TMSKeyBuilder keyBuilder, int maxConnections) {
        this.keyBuilder = keyBuilder;
        this.client = client;
        this.locks = locks;
        this.concurrency = maxConnections;
        this.deleteExecutor =
                createDeleteExecutorService(client.getContainerName(), maxConnections);
    }

    private static ExecutorService createDeleteExecutorService(
            String containerName, int parallelism) {
        ThreadFactory tf =
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat(
                                "GWC AzureBlobStore bulk delete thread-%d. Container: "
                                        + containerName)
                        .setPriority(Thread.MIN_PRIORITY)
                        .build();
        return Executors.newFixedThreadPool(parallelism, tf);
    }

    // Azure, like S3, truncates timestamps to seconds precision and does not allow to
    // programmatically set the last modified time
    private long currentTimeSeconds() {
        final long timestamp = (long) Math.ceil(System.currentTimeMillis() / 1000D) * 1000L;
        return timestamp;
    }

    /**
     * Executes the provided iterator of callables on the delete executor, returning their results
     */
    public void executeParallel(List<Callable<?>> callables) throws StorageException {
        List<Future> futures = new ArrayList<>();
        for (Callable<?> callable : callables) {
            futures.add(deleteExecutor.submit(callable));
        }
        for (Future future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                throw new StorageException("Failed to execute parallel delete", e);
            }
        }
    }

    /**
     * Executes the removal of the specified keys in a parallel fashion, returning the number of
     * removed keys
     */
    public Long deleteParallel(List<String> keys) throws StorageException {
        try {
            return new KeysBulkDelete(keys).call();
        } catch (Exception e) {
            throw new StorageException("Failed to submit parallel keys execution", e);
        }
    }

    public boolean scheduleAsyncDelete(final String prefix) throws StorageException {
        final long timestamp = currentTimeSeconds();
        String msg =
                String.format(
                        "Issuing bulk delete on '%s/%s' for objects older than %d",
                        client.getContainerName(), prefix, timestamp);
        log.info(msg);

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
                log.info(
                        String.format(
                                "Restarting pending bulk delete on '%s/%s':%d",
                                client.getContainerName(), prefix, timestamp));
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
        if (client.listBlobs(prefix, 1).size() == 0) {
            return false;
        }

        // is there any task already deleting a larger set of times in the same prefix folder?
        Long currentTaskTime = pendingDeletesKeyTime.get(prefix);
        if (currentTaskTime != null && currentTaskTime.longValue() > timestamp) {
            return false;
        }

        PrefixTimeBulkDelete task = new PrefixTimeBulkDelete(prefix, timestamp);
        deleteExecutor.submit(task);
        pendingDeletesKeyTime.put(prefix, timestamp);

        return true;
    }

    private void clearPendingBulkDelete(final String prefix, final long timestamp)
            throws GeoWebCacheException {
        Long taskTime = pendingDeletesKeyTime.get(prefix);
        if (taskTime == null) {
            return; // someone else cleared it up for us. A task that run after this one but
            // finished before?
        }
        if (taskTime.longValue() > timestamp) {
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
            } else {
                log.info(
                        String.format(
                                "bulk delete finished but there's a newer one ongoing for container '%s/%s'",
                                client.getContainerName(), prefix));
            }
        } catch (StorageException e) {
            throw new RuntimeException(e);
        } finally {
            lock.release();
        }
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
                log.info(
                        String.format(
                                "Running bulk delete on '%s/%s':%d",
                                client.getContainerName(), prefix, timestamp));

                ContainerURL container = client.getContainer();

                int jobPageSize = Math.max(concurrency, PAGE_SIZE);
                ListBlobsOptions options =
                        new ListBlobsOptions().withPrefix(prefix).withMaxResults(jobPageSize);
                ContainerListBlobFlatSegmentResponse response =
                        container.listBlobsFlatSegment(null, options, null).blockingGet();

                Predicate<BlobItem> filter =
                        blobItem -> {
                            long lastModified =
                                    blobItem.properties().lastModified().toEpochSecond() * 1000;
                            return timestamp >= lastModified;
                        };

                while (response.body().segment() != null) {
                    checkInterrupted();
                    deleteItems(container, response.body().segment(), filter);
                    String marker = response.body().nextMarker();
                    // marker will be empty if there is no next page
                    if (Strings.isNullOrEmpty(marker)) break;
                    // fetch next page
                    response = container.listBlobsFlatSegment(marker, options, null).blockingGet();
                }
            } catch (InterruptedException | IllegalStateException e) {
                log.info(
                        String.format(
                                "Azure bulk delete aborted for '%s/%s'. Will resume on next startup.",
                                client.getContainerName(), prefix));
                throw e;
            } catch (Exception e) {
                log.log(
                        Level.WARNING,
                        String.format(
                                "Unknown error performing bulk Azure blobs delete of '%s/%s'",
                                client.getContainerName(), prefix),
                        e);
                throw e;
            }

            log.info(
                    String.format(
                            "Finished bulk delete on '%s/%s':%d. %d objects deleted",
                            client.getContainerName(), prefix, timestamp, count));

            clearPendingBulkDelete(prefix, timestamp);
            return count;
        }

        private long deleteItems(
                ContainerURL container, BlobFlatListSegment segment, Predicate<BlobItem> filter)
                throws ExecutionException, InterruptedException {
            List<Future<Object>> collect =
                    segment.blobItems().stream()
                            .filter(item -> filter.test(item))
                            .map(
                                    item ->
                                            deleteExecutor.submit(
                                                    () -> {
                                                        deleteItem(container, item);
                                                        return null;
                                                    }))
                            .collect(Collectors.toList());

            for (Future<Object> f : collect) {
                f.get();
            }
            return collect.size();
        }

        private void deleteItem(ContainerURL container, BlobItem item) {
            String key = item.name();
            try {

                int status = container.createBlobURL(key).delete().blockingGet().statusCode();
                if (status != NOT_FOUND.value() && !HttpStatus.valueOf(status).is2xxSuccessful()) {
                    throw new RuntimeException(
                            "Deletion failed with status " + status + " on resource " + key);
                }
            } catch (RestException e) {
                if (e.response().statusCode() != NOT_FOUND.value()) {
                    throw new RuntimeException(
                            "Deletion failed with status "
                                    + e.response().statusCode()
                                    + " on resource "
                                    + key,
                            e);
                }
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
                if (log.isLoggable(Level.FINER)) {
                    log.finer(
                            String.format(
                                    "Running delete delete on list of items on '%s':%s ... (only the first 100 items listed)",
                                    client.getContainerName(),
                                    keys.subList(0, Math.min(keys.size(), 100))));
                }

                ContainerURL container = client.getContainer();

                for (int i = 0; i < keys.size(); i += PAGE_SIZE) {
                    deleteItems(container, keys.subList(i, Math.min(i + PAGE_SIZE, keys.size())));
                }
            } catch (InterruptedException | IllegalStateException e) {
                log.log(Level.INFO, "Azure bulk delete aborted", e);
                throw e;
            } catch (Exception e) {
                log.log(Level.WARNING, "Unknown error performing bulk Azure delete", e);
                throw e;
            }

            log.info(
                    String.format(
                            "Finished bulk delete on %s, %d objects deleted",
                            client.getContainerName(), count));
            return count;
        }

        private long deleteItems(ContainerURL container, List<String> itemNames)
                throws ExecutionException, InterruptedException {
            List<Future<Object>> collect =
                    itemNames.stream()
                            .map(
                                    item ->
                                            deleteExecutor.submit(
                                                    () -> {
                                                        return deleteItem(container, item);
                                                    }))
                            .collect(Collectors.toList());

            for (Future<Object> f : collect) {
                f.get();
            }
            return collect.size();
        }

        private Object deleteItem(ContainerURL container, String item) {
            try {
                int status = container.createBlobURL(item).delete().blockingGet().statusCode();
                if (status != NOT_FOUND.value() && !HttpStatus.valueOf(status).is2xxSuccessful()) {
                    throw new RuntimeException(
                            "Deletion failed with status " + status + " on resource " + item);
                }
            } catch (RestException e) {
                if (e.response().statusCode() != NOT_FOUND.value()) {
                    throw new RuntimeException(
                            "Deletion failed with status "
                                    + e.response().statusCode()
                                    + " on resource "
                                    + item,
                            e);
                }
            }
            return null;
        }
    }

    void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }
}
