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

import static java.lang.String.format;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.s3.callback.Callback;
import org.geowebcache.s3.callback.LockingDecorator;
import org.geowebcache.s3.callback.MarkPendingDeleteDecorator;
import org.geowebcache.s3.callback.StatisticCallbackDecorator;
import org.geowebcache.s3.delete.BulkDeleteTask;
import org.geowebcache.s3.delete.DeleteTilePrefix;
import org.geowebcache.s3.delete.DeleteTileRange;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.TMSKeyBuilder;

public class S3Ops {

    public static final int BATCH_SIZE = 1000;
    private final AmazonS3Client conn;

    private final String bucketName;

    private final TMSKeyBuilder keyBuilder;

    private final LockProvider locks;

    private final ExecutorService deleteExecutorService;

    private final Map<String, Long> pendingDeletesKeyTime = new ConcurrentHashMap<>();
    private final Logger logger;

    public S3Ops(AmazonS3Client conn, String bucketName, TMSKeyBuilder keyBuilder, LockProvider locks, Logger logger) {
        this.conn = conn;
        this.bucketName = bucketName;
        this.keyBuilder = keyBuilder;
        this.locks = locks == null ? new NoOpLockProvider() : locks;
        this.logger = logger;
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

    private void issuePendingBulkDeletes() {
        final String pendingDeletesKey = keyBuilder.pendingDeletes();
        final String assumedPrefix = "";

        Properties deletes = getProperties(pendingDeletesKey);
        for (Entry<Object, Object> e : deletes.entrySet()) {
            final String path = e.getKey().toString();
            final long timestamp = Long.parseLong(e.getValue().toString());
            logger.info(format("Restarting pending bulk delete on '%s/%s':%d", bucketName, path, timestamp));
            LockingDecorator lockingDecorator = new LockingDecorator(
                    new MarkPendingDeleteDecorator(new StatisticCallbackDecorator(logger), this, logger),
                    locks,
                    logger);
            DeleteTilePrefix deleteTilePrefix = new DeleteTilePrefix(assumedPrefix, bucketName, path);
            asyncBulkDelete(assumedPrefix, deleteTilePrefix, timestamp, lockingDecorator);
        }
    }

    public void clearPendingBulkDelete(final String prefix, final long timestamp) throws GeoWebCacheException {
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
            Properties deletes = getProperties(pendingDeletesKey);
            String storedVal = (String) deletes.remove(prefix);
            long storedTimestamp = storedVal == null ? Long.MIN_VALUE : Long.parseLong(storedVal);
            if (timestamp >= storedTimestamp) {
                putProperties(pendingDeletesKey, deletes);
            } else {
                logger.info(format(
                        "bulk delete finished but there's a newer one ongoing for bucket '%s/%s'", bucketName, prefix));
            }
        } catch (StorageException e) {
            throw new RuntimeException(e);
        } finally {
            lock.release();
        }
    }

    public boolean scheduleAsyncDelete(DeleteTileRange deleteTileRange, Callback callback) throws GeoWebCacheException {
        final long timestamp = currentTimeSeconds();
        String msg = format(
                "Issuing bulk delete on '%s/%s' for objects older than %d",
                bucketName, deleteTileRange.path(), timestamp);
        logger.info(msg);

        return asyncBulkDelete(deleteTileRange.path(), deleteTileRange, timestamp, callback);
    }

    // S3 truncates timestamps to seconds precision and does not allow to programmatically set
    // the last modified time
    public long currentTimeSeconds() {
        return (long) Math.ceil(System.currentTimeMillis() / 1000D) * 1000L;
    }

    private synchronized boolean asyncBulkDelete(
            final String prefix, final DeleteTileRange deleteTileRange, final long timestamp, final Callback callback) {

        if (!prefixExists(prefix)) {
            return false;
        }

        Long currentTaskTime = pendingDeletesKeyTime.get(prefix);
        if (currentTaskTime != null && currentTaskTime > timestamp) {
            return false;
        }

        var task = BulkDeleteTask.newBuilder()
                .withAmazonS3Wrapper(new AmazonS3Wrapper(conn))
                .withS3ObjectsWrapper(S3ObjectsWrapper.withPrefix(conn, bucketName, prefix))
                .withBucket(bucketName)
                .withDeleteRange(deleteTileRange)
                .withCallback(callback)
                .withBatch(BATCH_SIZE)
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

    private boolean isPendingDelete(S3Object object) {
        if (pendingDeletesKeyTime.isEmpty()) {
            return false;
        }
        final String key = object.getKey();
        final long lastModified = object.getObjectMetadata().getLastModified().getTime();
        for (Map.Entry<String, Long> e : pendingDeletesKeyTime.entrySet()) {
            String parentKey = e.getKey();
            if (key.startsWith(parentKey)) {
                long deleteTime = e.getValue();
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
                return IOUtils.toByteArray(in);
            }
        } catch (IOException e) {
            throw new StorageException("Error getting " + key, e);
        }
    }

    /** Simply checks if there are objects starting with {@code prefix} */
    public boolean prefixExists(String prefix) {
        return S3Objects.withPrefix(conn, bucketName, prefix)
                .withBatchSize(1)
                .iterator()
                .hasNext();
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
}
