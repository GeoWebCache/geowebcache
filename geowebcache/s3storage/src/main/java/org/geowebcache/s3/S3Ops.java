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

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.io.IOUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.s3.callback.Callback;
import org.geowebcache.s3.callback.LockingDecorator;
import org.geowebcache.s3.delete.BulkDeleteTask;
import org.geowebcache.s3.delete.DeleteTileRange;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.TMSKeyBuilder;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;

public class S3Ops {

    private final AmazonS3Client conn;

    private final String bucketName;

    private final TMSKeyBuilder keyBuilder;

    private final LockProvider locks;

    private final ExecutorService deleteExecutorService;

    private final Map<String, Long> pendingDeletesKeyTime = new ConcurrentHashMap<>();

    public S3Ops(AmazonS3Client conn, String bucketName, TMSKeyBuilder keyBuilder, LockProvider locks)
            throws StorageException {
        this.conn = conn;
        this.bucketName = bucketName;
        this.keyBuilder = keyBuilder;
        this.locks = locks == null ? new NoOpLockProvider() : locks;
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
                S3BlobStore.getLog()
                        .info(format("Restarting pending bulk delete on '%s/%s':%d", bucketName, prefix, timestamp));
                // asyncDelete(prefix, timestamp);
            }
        } finally {
            try {
                lock.release();
            } catch (GeoWebCacheException e) {
                throw new StorageException("Unable to unlock pending deletes", e);
            }
        }
    }

    public void clearPendingBulkDelete(final String prefix, final long timestamp) throws GeoWebCacheException {
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
            } else {
                S3BlobStore.getLog()
                        .info(format(
                                "bulk delete finished but there's a newer one ongoing for bucket '%s/%s'",
                                bucketName, prefix));
            }
        } catch (StorageException e) {
            throw new RuntimeException(e);
        } finally {
            lock.release();
        }
    }

    public boolean scheduleAsyncDelete(
            DeleteTileRange deleteTileRange, Callback callback, LockingDecorator lockingDecorator)
            throws GeoWebCacheException {
        final long timestamp = currentTimeSeconds();
        String msg = format(
                "Issuing bulk delete on '%s/%s' for objects older than %d",
                bucketName, deleteTileRange.path(), timestamp);
        S3BlobStore.getLog().info(msg);

        return asyncBulkDelete(deleteTileRange.path(), deleteTileRange, timestamp, callback);
    }

    // S3 truncates timestamps to seconds precision and does not allow to programmatically set
    // the last modified time
    public long currentTimeSeconds() {
        final long timestamp = (long) Math.ceil(System.currentTimeMillis() / 1000D) * 1000L;
        return timestamp;
    }

    private synchronized boolean asyncBulkDelete(
            final String prefix, final DeleteTileRange deleteTileRange, final long timestamp, final Callback callback) {

        if (!prefixExists(prefix)) {
            return false;
        }

        Long currentTaskTime = pendingDeletesKeyTime.get(prefix);
        if (currentTaskTime != null && currentTaskTime.longValue() > timestamp) {
            return false;
        }

        var task = BulkDeleteTask.newBuilder()
                .withAmazonS3Wrapper(new AmazonS3Wrapper(conn))
                .withS3ObjectsWrapper(S3ObjectsWrapper.withPrefix(conn, bucketName, prefix))
                .withBucket(bucketName)
                .withDeleteRange(deleteTileRange)
                .withCallback(callback)
                .withBatch(1000)
                .build();

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
        boolean hasNext = S3Objects.withPrefix(conn, bucketName, prefix)
                .withBatchSize(1)
                .iterator()
                .hasNext();
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
