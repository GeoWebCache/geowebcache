/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */
package org.geowebcache.s3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.storage.StorageException;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

class S3Ops {

    private final AmazonS3Client conn;

    private final String bucketName;

    private final TMSKeyBuilder keyBuilder;

    private final LockProvider locks;

    private ExecutorService deleteExecutorService;

    private Map<String, Long> pendingDeletesKeyTime = new ConcurrentHashMap<>();

    public S3Ops(AmazonS3Client conn, String bucketName, TMSKeyBuilder keyBuilder,
            LockProvider locks) throws StorageException {
        this.conn = conn;
        this.bucketName = bucketName;
        this.keyBuilder = keyBuilder;
        this.locks = locks == null ? new NoOpLockProvider() : locks;
        this.deleteExecutorService = createDeleteExecutorService();
        issuePendingBulkDeletes();
    }

    private ExecutorService createDeleteExecutorService() {
        ThreadFactory tf = new ThreadFactoryBuilder().setDaemon(true)
                .setNameFormat("GWC S3BlobStore bulk delete thread-%d. Bucket: " + bucketName)
                .setPriority(Thread.MIN_PRIORITY).build();
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
                S3BlobStore.log.info(String.format("Restarting pending bulk delete on '%s/%s':%d",
                        bucketName, prefix, timestamp));
                asyncDelete(prefix, timestamp);
            }
        } finally {
            try {
                lock.release();
            } catch (GeoWebCacheException e) {
                throw new StorageException("Unable to unlock pending deletes", e);
            }
        }
    }

    private void clearPendingBulkDelete(final String prefix, final long timestamp)
            throws GeoWebCacheException {
        Long taskTime = pendingDeletesKeyTime.get(prefix);
        if (taskTime == null) {
            return; // someone else cleared it up for us. A task that run after this one but
                    // finished before?
        }
        if (taskTime.longValue() > timestamp) {
            return;// someone else issued a bulk delete after this one for the same key prefix
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
                S3BlobStore.log.info(String.format(
                        "bulk delete finished but there's a newer one ongoing for bucket '%s/%s'",
                        bucketName, prefix));
            }
        } catch (StorageException e) {
            Throwables.propagate(e);
        } finally {
            lock.release();
        }
    }

    public boolean scheduleAsyncDelete(final String prefix) throws GeoWebCacheException {
        final long timestamp = currentTimeSeconds();
        String msg = String.format("Issuing bulk delete on '%s/%s' for objects older than %d",
                bucketName, prefix, timestamp);
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
            throw Throwables.propagate(e);
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

    private synchronized boolean asyncDelete(final String prefix, final long timestamp) {
        if (!prefixExists(prefix)) {
            return false;
        }

        Long currentTaskTime = pendingDeletesKeyTime.get(prefix);
        if (currentTaskTime != null && currentTaskTime.longValue() > timestamp) {
            return false;
        }

        BulkDelete task = new BulkDelete(conn, bucketName, prefix, timestamp);
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
            if (404 != e.getStatusCode()) {// 404 == not found
                throw new StorageException("Error checking existence of " + key + ": "
                        + e.getMessage(), e);
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
            if (404 == e.getStatusCode()) {// 404 == not found
                return null;
            }
            throw new StorageException("Error fetching " + key + ": " + e.getMessage(), e);
        }
        if (isPendingDelete(object)) {
            return null;
        }
        return object;
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
        S3Object object = getObject(key);
        if (object == null) {
            return null;
        }
        try (S3ObjectInputStream in = object.getObjectContent()) {
            byte[] bytes = IOUtils.toByteArray(in);
            return bytes;
        } catch (IOException e) {
            throw new StorageException("Error getting " + key, e);
        }

    }

    /**
     * Simply checks if there are objects starting with {@code prefix}
     */
    public boolean prefixExists(String prefix) {
        boolean hasNext = S3Objects.withPrefix(conn, bucketName, prefix).withBatchSize(1)
                .iterator().hasNext();
        return hasNext;
    }

    public Properties getProperties(String key) {
        Properties properties = new Properties();
        byte[] bytes;
        try {
            bytes = getBytes(key);
        } catch (StorageException e) {
            throw Throwables.propagate(e);
        }
        if (bytes != null) {
            try {
                properties.load(new InputStreamReader(new ByteArrayInputStream(bytes),
                        StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        return properties;
    }

    public void putProperties(String resourceKey, Properties properties) throws StorageException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            properties.store(out, "");
        } catch (IOException e) {
            throw Throwables.propagate(e);
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
        return StreamSupport.stream(S3Objects.withPrefix(conn, bucketName, prefix).spliterator(), false);
    }

    private class BulkDelete implements Callable<Long> {

        private final String prefix;

        private final long timestamp;

        private final AmazonS3 conn;

        private final String bucketName;

        public BulkDelete(final AmazonS3 conn, final String bucketName, final String prefix,
                final long timestamp) {
            this.conn = conn;
            this.bucketName = bucketName;
            this.prefix = prefix;
            this.timestamp = timestamp;
        }

        @Override
        public Long call() throws Exception {
            long count = 0L;
            try {
                checkInterrupted();
                S3BlobStore.log.info(String.format("Running bulk delete on '%s/%s':%d", bucketName,
                        prefix, timestamp));
                Predicate<S3ObjectSummary> filter = new TimeStampFilter(timestamp);
                AtomicInteger n = new AtomicInteger(0); 
                Iterable<List<S3ObjectSummary>> partitions = objectStream(prefix)
                        .filter(filter)
                        .collect(Collectors.groupingBy((x)->n.getAndIncrement()%1000))
                        .values();

                for (List<S3ObjectSummary> partition : partitions) {

                    checkInterrupted();

                    List<KeyVersion> keys = new ArrayList<>(partition.size());
                    for (S3ObjectSummary so : partition) {
                        String key = so.getKey();
                        keys.add(new KeyVersion(key));
                    }

                    checkInterrupted();

                    if (!keys.isEmpty()) {
                        DeleteObjectsRequest deleteReq = new DeleteObjectsRequest(bucketName);
                        deleteReq.setQuiet(true);
                        deleteReq.setKeys(keys);

                        checkInterrupted();

                        conn.deleteObjects(deleteReq);
                        count += keys.size();
                    }
                }
            } catch (InterruptedException | IllegalStateException e) {
                S3BlobStore.log.info(String.format(
                        "S3 bulk delete aborted for '%s/%s'. Will resume on next startup.",
                        bucketName, prefix));
                return null;
            } catch (Exception e) {
                S3BlobStore.log.warn(String.format(
                        "Unknown error performing bulk S3 delete of '%s/%s'", bucketName, prefix),
                        e);
                throw e;
            }

            S3BlobStore.log.info(String.format(
                    "Finished bulk delete on '%s/%s':%d. %d objects deleted", bucketName, prefix,
                    timestamp, count));

            S3Ops.this.clearPendingBulkDelete(prefix, timestamp);
            return count;
        }

        private void checkInterrupted() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
    }

    /**
     * Filters objects that are newer than the given timestamp
     *
     */
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
