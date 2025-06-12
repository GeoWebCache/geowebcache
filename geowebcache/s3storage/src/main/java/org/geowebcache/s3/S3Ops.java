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
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */
package org.geowebcache.s3;

import static org.geowebcache.s3.S3BlobStore.Bounds.prefixWithoutBounds;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.s3.S3BlobStore.Bounds;
import org.geowebcache.s3.streams.BatchingIterator;
import org.geowebcache.s3.streams.BoundedS3KeySupplier;
import org.geowebcache.s3.streams.DeleteBatchesOfS3Objects;
import org.geowebcache.s3.streams.TileDeletionListenerNotifier;
import org.geowebcache.s3.streams.UnboundedS3KeySupplier;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.TMSKeyBuilder;

class S3Ops {
    private static final int BATCH_SIZE = 1000;
    public static final Consumer<List<S3ObjectSummary>> NO_OPERATION_POST_PROCESSOR = list -> {};
    private final AmazonS3Client conn;

    private final String bucketName;

    private final TMSKeyBuilder keyBuilder;

    private final LockProvider locks;

    private ExecutorService deleteExecutorService;

    private Map<String, Long> pendingDeletesKeyTime = new ConcurrentHashMap<>();

    private final BlobStoreListenerList listeners;

    public S3Ops(
            AmazonS3Client conn,
            String bucketName,
            TMSKeyBuilder keyBuilder,
            LockProvider locks,
            BlobStoreListenerList listeners)
            throws StorageException {
        this.conn = conn;
        this.bucketName = bucketName;
        this.keyBuilder = keyBuilder;
        this.locks = locks == null ? new NoOpLockProvider() : locks;
        this.listeners = listeners;
        this.deleteExecutorService = createDeleteExecutorService();
        issuePendingBulkDeletes();
    }

    private ExecutorService createDeleteExecutorService() {
        ThreadFactory tf = new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("GWC S3BlobStore bulk delete thread-%d. Bucket: " + bucketName)
                .setPriority(Thread.MIN_PRIORITY)
                .build();
        return Executors.newCachedThreadPool(tf);
    }

    public void shutDown() {
        deleteExecutorService.shutdownNow();
    }

    @SuppressWarnings("Finally")
    private void issuePendingBulkDeletes() throws StorageException {
        final String pendingDeletesKey = keyBuilder.pendingDeletes();
        Lock lock;
        try {
            lock = locks.getLock(pendingDeletesKey);
        } catch (GeoWebCacheException e) {
            throw new StorageException("Unable to lock pending deletes", e);
        }

        try {
            Properties deletes = getProperties(pendingDeletesKey);
            for (Entry<Object, Object> e : deletes.entrySet()) {
                final String prefix = e.getKey().toString();
                final long timestamp = Long.parseLong(e.getValue().toString());
                S3BlobStore.log.info(
                        "Restarting pending bulk delete on '%s/%s':%d".formatted(bucketName, prefix, timestamp));
                pendingDeletesKeyTime.put(prefix, timestamp);
                boolean nothingToDelete = !asyncDelete(prefix, timestamp);
                if (nothingToDelete) {
                    clearPendingBulkDelete(prefix, timestamp);
                }
            }
        } catch (GeoWebCacheException e) {
            S3BlobStore.log.warning("Unable to delete pending deletes: " + e.getMessage());
        } finally {
            try {
                lock.release();
            } catch (GeoWebCacheException e) {
                throw new StorageException("Unable to unlock pending deletes", e);
            }
        }
    }

    private void clearPendingBulkDelete(final String prefix, final long timestamp) throws GeoWebCacheException {
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
            Properties deletes = getProperties(pendingDeletesKey);
            String storedVal = (String) deletes.remove(prefix);
            long storedTimestamp = storedVal == null ? Long.MIN_VALUE : Long.parseLong(storedVal);
            if (timestamp >= storedTimestamp) {
                putProperties(pendingDeletesKey, deletes);
                S3BlobStore.log.info(
                        "Bulk delete removed pendingDelete for for bucket '%s/%s'".formatted(bucketName, prefix));

            } else {
                S3BlobStore.log.info("Bulk delete finished but there's a newer one ongoing for bucket '%s/%s'"
                        .formatted(bucketName, prefix));
            }
        } catch (StorageException e) {
            throw new RuntimeException(e);
        } finally {
            lock.release();
        }
    }

    public boolean scheduleAsyncDelete(final String prefix) throws GeoWebCacheException {
        final long timestamp = currentTimeSeconds();
        String msg =
                "Issuing bulk delete on '%s/%s' for objects older than %d".formatted(bucketName, prefix, timestamp);
        S3BlobStore.log.info(msg);

        Lock lock = locks.getLock(prefix);
        try {
            boolean taskRuns = asyncDelete(prefix, timestamp);
            if (taskRuns) {
                final String pendingDeletesKey = keyBuilder.pendingDeletes();
                Properties deletes = getProperties(pendingDeletesKey);
                deletes.setProperty(prefix, String.valueOf(timestamp));
                putProperties(pendingDeletesKey, deletes);
            }
            return taskRuns;
        } catch (StorageException e) {
            throw new RuntimeException(e);
        } finally {
            lock.release();
        }
    }

    // S3 truncates timestamps to seconds precision and does not allow to programmatically set
    // the last modified time
    private long currentTimeSeconds() {
        final long timestamp = (long) Math.ceil(System.currentTimeMillis() / 1000D) * 1000L;
        return timestamp;
    }

    private synchronized boolean asyncDelete(final String prefix, final long timestamp) throws StorageException {
        if (!prefixExists(prefix)) {
            return false;
        }

        Long currentTaskTime = pendingDeletesKeyTime.get(prefix);
        if (currentTaskTime != null && currentTaskTime.longValue() > timestamp) {
            return false;
        }

        TileDeletionListenerNotifier tileDeletionListenerNotifier =
                new TileDeletionListenerNotifier(listeners, keyBuilder, S3BlobStore.log);
        BulkDelete task =
                new BulkDelete(conn, bucketName, prefix, timestamp, S3BlobStore.log, tileDeletionListenerNotifier);
        deleteExecutorService.submit(task);
        pendingDeletesKeyTime.put(prefix, timestamp);

        return true;
    }

    @Nullable
    public ObjectMetadata getObjectMetadata(String key) throws StorageException {
        ObjectMetadata obj = null;
        try {
            obj = conn.getObjectMetadata(bucketName, key);
        } catch (AmazonS3Exception e) {
            if (404 != e.getStatusCode()) { // 404 == not found
                throw new StorageException("Error checking existence of " + key + ": " + e.getMessage(), e);
            }
        }
        return obj;
    }

    public void putObject(PutObjectRequest putObjectRequest) throws StorageException {
        try {
            conn.putObject(putObjectRequest);
        } catch (RuntimeException e) {
            throw new StorageException("Error storing " + putObjectRequest.getKey(), e);
        }
    }

    @Nullable
    public S3Object getObject(String key) throws StorageException {
        final S3Object object;
        try {
            object = conn.getObject(bucketName, key);
        } catch (AmazonS3Exception e) {
            if (404 == e.getStatusCode()) { // 404 == not found
                return null;
            }
            throw new StorageException("Error fetching " + key + ": " + e.getMessage(), e);
        }
        if (isPendingDelete(object)) {
            closeObject(object);
            return null;
        }
        return object;
    }

    private void closeObject(S3Object object) throws StorageException {
        try {
            object.close();
        } catch (IOException e) {
            throw new StorageException("Error closing connection to " + object.getKey() + ": " + e.getMessage(), e);
        }
    }

    public boolean deleteObject(final String key) {
        try {
            conn.deleteObject(bucketName, key);
        } catch (AmazonS3Exception e) {
            return false;
        }
        return true;
    }

    private boolean isPendingDelete(S3Object object) {
        if (pendingDeletesKeyTime.isEmpty()) {
            return false;
        }
        final String key = object.getKey();
        final long lastModified = object.getObjectMetadata().getLastModified().getTime();
        for (Map.Entry<String, Long> e : pendingDeletesKeyTime.entrySet()) {
            String parentKey = e.getKey();
            if (key.startsWith(parentKey)) {
                long deleteTime = e.getValue().longValue();
                return deleteTime >= lastModified;
            }
        }
        return false;
    }

    @Nullable
    public byte[] getBytes(String key) throws StorageException {
        try (S3Object object = getObject(key)) {
            if (object == null) {
                return null;
            }
            try (S3ObjectInputStream in = object.getObjectContent()) {
                byte[] bytes = IOUtils.toByteArray(in);
                return bytes;
            }
        } catch (IOException e) {
            throw new StorageException("Error getting " + key, e);
        }
    }

    /** Simply checks if there are objects starting with {@code prefix} */
    public boolean prefixExists(String prefix) {
        String prefixWithoutBounds = prefixWithoutBounds(prefix);
        boolean hasNext = S3Objects.withPrefix(conn, bucketName, prefixWithoutBounds)
                .withBatchSize(1)
                .iterator()
                .hasNext();

        if (!hasNext) {
            S3BlobStore.log.info("No prefix exists for " + prefixWithoutBounds);
        }
        return hasNext;
    }

    public Properties getProperties(String key) {
        Properties properties = new Properties();
        byte[] bytes;
        try {
            bytes = getBytes(key);
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
        if (bytes != null) {
            try {
                properties.load(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return properties;
    }

    public void putProperties(String resourceKey, Properties properties) throws StorageException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            properties.store(out, "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        byte[] bytes = out.toByteArray();
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(bytes.length);
        objectMetadata.setContentType("text/plain");

        InputStream in = new ByteArrayInputStream(bytes);
        PutObjectRequest putReq = new PutObjectRequest(bucketName, resourceKey, in, objectMetadata);
        putObject(putReq);
    }

    public Stream<S3ObjectSummary> objectStream(String prefix) {
        return StreamSupport.stream(
                S3Objects.withPrefix(conn, bucketName, prefix).spliterator(), false);
    }

    public class BulkDelete implements Callable<Long> {

        private final String prefix;

        private final long timestamp;

        private final AmazonS3 conn;

        private final String bucketName;
        private final Logger logger;
        private final TileDeletionListenerNotifier tileDeletionListenerNotifier;

        public BulkDelete(
                final AmazonS3 conn,
                final String bucketName,
                final String prefix,
                final long timestamp,
                final Logger logger,
                TileDeletionListenerNotifier tileDeletionListenerNotifier) {
            this.conn = conn;
            this.bucketName = bucketName;
            this.prefix = prefix;
            this.timestamp = timestamp;
            this.logger = logger;
            this.tileDeletionListenerNotifier = tileDeletionListenerNotifier;
        }

        @Override
        public Long call() throws Exception {
            LockProvider.Lock lock = locks.getLock(prefix);
            logger.info("Running bulk delete on '%s/%s':%d".formatted(bucketName, prefix, timestamp));
            try {
                long tilesDeleted = deleteBatchesOfTilesAndInformListeners();
                logger.info("Finished bulk delete on '%s/%s':%d. %d objects deleted"
                        .formatted(bucketName, prefix, timestamp, tilesDeleted));

                // Once clear of the streams, throw the interrupt exception if required
                // Streams will exit cleanly without clearing the interrupt flag
                checkInterrupted();
                clearPendingBulkDelete(prefix, timestamp);
                return tilesDeleted;
            } catch (RuntimeException e) {
                S3BlobStore.log.warning("Aborted bulk delete '" + e.getMessage() + "' from "
                        + e.getClass().getSimpleName());
                if (Objects.nonNull(e.getMessage())) {
                    S3BlobStore.log.warning("Aborted caused '" + e.getCause().getMessage() + "' from "
                            + e.getCause().getClass().getSimpleName());
                }
                throw e;
            } finally {
                try {
                    lock.release();
                } catch (GeoWebCacheException e) {
                    // Do not allow checked exception to escape from a finally block
                    logger.warning("Error releasing lock: " + e.getMessage());
                }
            }
        }

        private long deleteBatchesOfTilesAndInformListeners() {
            var possibleBounds = Bounds.createBounds(prefix);
            DeleteBatchesOfS3Objects<S3ObjectSummary> deleteBatchesOfS3Objects =
                    new DeleteBatchesOfS3Objects<>(bucketName, conn, S3ObjectSummary::getKey, logger);
            Predicate<S3ObjectSummary> timeStampFilter = new TimeStampFilter(timestamp);
            Consumer<List<S3ObjectSummary>> batchPostProcessor =
                    possibleBounds.isPresent() ? tileDeletionListenerNotifier : NO_OPERATION_POST_PROCESSOR;

            return BatchingIterator.batchedStreamOf(
                            createS3ObjectStream(possibleBounds)
                                    .takeWhile(Objects::nonNull)
                                    .takeWhile(o -> !Thread.currentThread().isInterrupted())
                                    .filter(timeStampFilter),
                            BATCH_SIZE)
                    .map(deleteBatchesOfS3Objects)
                    .peek(batchPostProcessor)
                    .mapToLong(List::size)
                    .sum();
        }

        private Stream<S3ObjectSummary> createS3ObjectStream(Optional<Bounds> possibleBounds) {
            if (possibleBounds.isPresent()) {
                String prefixWithoutBounds = prefixWithoutBounds(prefix);
                return boundedStreamOfS3Objects(prefixWithoutBounds, possibleBounds.get());
            } else {
                return unboundedStreamOfS3Objects(prefix);
            }
        }

        private Stream<S3ObjectSummary> unboundedStreamOfS3Objects(String prefix) {
            S3Objects s3Objects = S3Objects.withPrefix(conn, bucketName, prefix).withBatchSize(BATCH_SIZE);
            UnboundedS3KeySupplier supplier = new UnboundedS3KeySupplier(prefix, bucketName, s3Objects, logger);
            return Stream.generate(supplier).takeWhile(Objects::nonNull);
        }

        private Stream<S3ObjectSummary> boundedStreamOfS3Objects(String prefixWithoutBounds, Bounds bounds) {
            BoundedS3KeySupplier supplier =
                    new BoundedS3KeySupplier(prefixWithoutBounds, logger, conn, bounds, bucketName, BATCH_SIZE);
            return Stream.generate(supplier)
                    .takeWhile(Objects::nonNull)
                    .filter(bounds::predicate); // Filter Y bounds as X is taken care of by the supplier
        }

        private void checkInterrupted() throws InterruptedException {
            if (Thread.interrupted()) {
                S3BlobStore.log.info("S3 bulk delete aborted for '%s/%s'. Will resume on next startup."
                        .formatted(bucketName, prefix));
                throw new InterruptedException();
            }
        }
    }

    /** Filters objects that are newer than the given timestamp */
    private static class TimeStampFilter implements Predicate<S3ObjectSummary> {

        private long timeStamp;

        public TimeStampFilter(long timeStamp) {
            this.timeStamp = timeStamp;
        }

        @Override
        public boolean test(S3ObjectSummary summary) {
            long lastModified = summary.getLastModified().getTime();
            boolean applies = timeStamp >= lastModified;
            return applies;
        }
    }
}
