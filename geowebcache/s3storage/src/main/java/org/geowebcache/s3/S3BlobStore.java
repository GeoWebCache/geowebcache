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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.google.common.io.ByteStreams;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.s3.callback.*;
import org.geowebcache.s3.delete.*;
import org.geowebcache.storage.*;
import org.geowebcache.util.TMSKeyBuilder;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.isNull;

public class S3BlobStore implements BlobStore {

    private static final Logger log = Logging.getLogger(S3BlobStore.class.getName());

    private final BlobStoreListenerList listeners = new BlobStoreListenerList();

    private AmazonS3Client conn;

    private final TMSKeyBuilder keyBuilder;

    private final String bucketName;

    private volatile boolean shutDown;

    private final S3Ops s3Ops;

    private final CannedAccessControlList acl;
    private final LockProvider lockProvider;

    public S3BlobStore(S3BlobStoreInfo config, TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {
        checkNotNull(config);
        checkNotNull(layers);

        this.bucketName = config.getBucket();
        String prefix = config.getPrefix() == null ? "" : config.getPrefix();
        this.keyBuilder = new TMSKeyBuilder(prefix, layers);
        conn = validateClient(config.buildClient(), bucketName);
        acl = config.getAccessControlList();

        this.s3Ops = new S3Ops(conn, bucketName, keyBuilder, lockProvider, log);

        boolean empty = !s3Ops.prefixExists(prefix);
        boolean existing = Objects.nonNull(s3Ops.getObjectMetadata(keyBuilder.storeMetadata()));

        CompositeBlobStore.checkSuitability(config.getLocation(), existing, empty);

        // TODO replace this with real metadata.  For now it's just a marker
        // to indicate this is a GWC cache.
        s3Ops.putProperties(keyBuilder.storeMetadata(), new Properties());
        this.lockProvider = lockProvider;
    }

    /**
     * Validates the client connection by running some {@link S3ClientChecker}, returns the validated client on success,
     * otherwise throws an exception
     */
    protected AmazonS3Client validateClient(AmazonS3Client client, String bucketName) throws StorageException {
        List<S3ClientChecker> connectionCheckers = Arrays.asList(this::checkBucketPolicy, this::checkAccessControlList);
        List<Exception> exceptions = new ArrayList<>();
        for (S3ClientChecker checker : connectionCheckers) {
            try {
                checker.validate(client, bucketName);
                break;
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (exceptions.size() == connectionCheckers.size()) {
            String messages = exceptions.stream().map(Throwable::getMessage).collect(Collectors.joining("\n"));
            throw new StorageException(
                    "Could not validate the connection to S3, exceptions gathered during checks:\n " + messages);
        }

        return client;
    }

    /** Implemented by lambdas testing an {@link AmazonS3Client} */
    interface S3ClientChecker {
        void validate(AmazonS3Client client, String bucketName) throws Exception;
    }

    /**
     * Checks a {@link com.amazonaws.services.s3.AmazonS3Client} by getting the ACL out of the bucket, as implemented by
     * S3, Cohesity, but not, for example, by Minio.
     */
    private void checkAccessControlList(AmazonS3Client client, String bucketName) throws Exception {
        try {
            log.fine("Checking access rights to bucket " + bucketName);
            AccessControlList bucketAcl = client.getBucketAcl(bucketName);
            List<Grant> grants = bucketAcl.getGrantsAsList();
            log.fine("Bucket " + bucketName + " permissions: " + grants);
        } catch (AmazonServiceException se) {
            throw new StorageException("Server error listing bucket ACLs: " + se.getMessage(), se);
        }
    }

    /**
     * Checks a {@link com.amazonaws.services.s3.AmazonS3Client} by getting the policy out of the bucket, as implemented
     * by S3, Minio, but not, for example, by Cohesity.
     */
    private void checkBucketPolicy(AmazonS3Client client, String bucketName) throws Exception {
        try {
            log.fine("Checking policy for bucket " + bucketName);
            BucketPolicy bucketPol = client.getBucketPolicy(bucketName);
            log.fine("Bucket " + bucketName + " policy: " + bucketPol.getPolicyText());
        } catch (AmazonServiceException se) {
            throw new StorageException("Server error getting bucket policy: " + se.getMessage(), se);
        }
    }

    @Override
    public void destroy() {
        this.shutDown = true;
        AmazonS3Client conn = this.conn;
        this.conn = null;
        if (conn != null) {
            s3Ops.shutDown();
            conn.shutdown();
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
    public void put(TileObject obj) throws StorageException {
        final Resource blob = obj.getBlob();
        checkNotNull(blob);
        checkNotNull(obj.getBlobFormat());

        final String key = keyBuilder.forTile(obj);
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(blob.getSize());

        String blobFormat = obj.getBlobFormat();
        String mimeType;
        try {
            mimeType = MimeType.createFromFormat(blobFormat).getMimeType();
        } catch (MimeException me) {
            throw new RuntimeException(me);
        }
        objectMetadata.setContentType(mimeType);

        // don't bother for the extra call if there are no listeners
        final boolean existed;
        ObjectMetadata oldObj;
        if (listeners.isEmpty()) {
            existed = false;
            oldObj = null;
        } else {
            oldObj = s3Ops.getObjectMetadata(key);
            existed = oldObj != null;
        }

        final ByteArrayInputStream input = toByteArray(blob);
        PutObjectRequest putObjectRequest =
                new PutObjectRequest(bucketName, key, input, objectMetadata).withCannedAcl(acl);

        log.finer(log.isLoggable(Level.FINER) ? ("Storing " + key) : "");
        s3Ops.putObject(putObjectRequest);

        putParametersMetadata(obj.getLayerName(), obj.getParametersId(), obj.getParameters());

        /*
         * This is important because listeners may be tracking tile existence
         */
        if (!listeners.isEmpty()) {
            if (existed) {
                long oldSize = oldObj.getContentLength();
                listeners.sendTileUpdated(obj, oldSize);
            } else {
                listeners.sendTileStored(obj);
            }
        }
    }

    private ByteArrayInputStream toByteArray(final Resource blob) throws StorageException {
        final byte[] bytes;
        if (blob instanceof ByteArrayResource) {
            bytes = ((ByteArrayResource) blob).getContents();
        } else {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream((int) blob.getSize());
                    WritableByteChannel channel = Channels.newChannel(out)) {
                blob.transferTo(channel);
                bytes = out.toByteArray();
            } catch (IOException e) {
                throw new StorageException("Error copying blob contents", e);
            }
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        final String key = keyBuilder.forTile(obj);
        try (S3Object object = s3Ops.getObject(key)) {
            if (object == null) {
                return false;
            }
            try (S3ObjectInputStream in = object.getObjectContent()) {
                byte[] bytes = ByteStreams.toByteArray(in);
                obj.setBlobSize(bytes.length);
                obj.setBlob(new ByteArrayResource(bytes));
                obj.setCreated(object.getObjectMetadata().getLastModified().getTime());
            }
        } catch (IOException e) {
            throw new StorageException("Error getting " + key, e);
        }
        return true;
    }

    @Override
    public boolean delete(final TileRange tileRange) {

        String layerName = tileRange.getLayerName();
        String layerId = keyBuilder.layerId(layerName);

        MimeType mimeType = tileRange.getMimeType();
        String shortFormat = mimeType.getFileExtension(); // png, png8, png24, etc

        CompositeDeleteTileRange deleteTileRange =
                new CompositeDeleteTilesInRange(keyBuilder.getPrefix(), bucketName, layerId, shortFormat, tileRange);

        Callback callback = new NotificationDecorator(new StatisticCallbackDecorator(log), listeners, log);

        try {
            return s3Ops.scheduleAsyncDelete(deleteTileRange, callback, null);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean delete(String layerName) {
        checkNotNull(layerName, "layerName");

        final String metadataKey = keyBuilder.layerMetadata(layerName);
        final String layerId = keyBuilder.layerId(layerName);

        s3Ops.deleteObject(metadataKey);

        DeleteTileRange deleteLayer = new DeleteTileLayer(keyBuilder.getPrefix(), bucketName, layerId, layerName);

        var lockingDecorator = new LockingDecorator(
                new MarkPendingDeleteDecorator(
                        new NotificationDecorator(new StatisticCallbackDecorator(log), listeners, log),
                        s3Ops,
                        S3BlobStore.log),
                lockProvider,
                S3BlobStore.log);

        boolean layerExists;
        try {
            layerExists = s3Ops.scheduleAsyncDelete(deleteLayer, lockingDecorator, lockingDecorator);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
        return layerExists;
    }

    @Override
    public boolean deleteByGridsetId(final String layerName, final String gridSetId) {

        checkNotNull(layerName, "layerName");
        checkNotNull(gridSetId, "gridSetId");

        var layerId = keyBuilder.layerId(layerName);
        var deleteTileGridSet =
                new DeleteTileGridSet(keyBuilder.getPrefix(), bucketName, layerId, gridSetId, layerName);

        var lockingDecorator = new LockingDecorator(
                new NotificationDecorator(new StatisticCallbackDecorator(log), listeners, log),
                lockProvider,
                S3BlobStore.log);

        boolean prefixExists;
        try {
            prefixExists = s3Ops.scheduleAsyncDelete(deleteTileGridSet, lockingDecorator, lockingDecorator);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }

        return prefixExists;
    }

    @Override
    public boolean delete(TileObject obj) {
        final String key = keyBuilder.forTile(obj);

        try {
            DeleteTileObject deleteTile = new DeleteTileObject(obj, key);
            Callback callback;
            if (listeners.isEmpty()) {
                callback = new StatisticCallbackDecorator(log);
            } else {
                callback = new NotificationDecorator(new StatisticCallbackDecorator(log), listeners, log);
            }
            return s3Ops.scheduleAsyncDelete(deleteTile, callback, null);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) {
        log.fine("No need to rename layers, S3BlobStore uses layer id as key root");
        if (s3Ops.prefixExists(oldLayerName)) {
            listeners.sendLayerRenamed(oldLayerName, newLayerName);
        }
        return true;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("clear() should not be called");
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
            s3Ops.putProperties(resourceKey, properties);
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
    }

    private Properties getLayerMetadata(String layerName) {
        String key = keyBuilder.layerMetadata(layerName);
        return s3Ops.getProperties(key);
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
            s3Ops.putProperties(resourceKey, properties);
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean layerExists(String layerName) {
        final String coordsPrefix = keyBuilder.forLayer(layerName);
        return s3Ops.prefixExists(coordsPrefix);
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId) {
        checkNotNull(layerName, "layerName");
        checkNotNull(parametersId, "parametersId");

        String layerId = keyBuilder.layerId(layerName);
        Set<String> gridSetIds = keyBuilder.layerGridsets(layerName);
        Set<String> formats = keyBuilder.layerFormats(layerName);

        CompositeDeleteTileParameterId deleteTileRange = new CompositeDeleteTileParameterId(
                keyBuilder.getPrefix(), bucketName, layerId, gridSetIds, formats, parametersId, layerName);

        var lockingCallback = new LockingDecorator(
                new NotificationDecorator(new StatisticCallbackDecorator(log), listeners, log),
                lockProvider,
                S3BlobStore.log);

        try {
            return s3Ops.scheduleAsyncDelete(deleteTileRange, lockingCallback, lockingCallback);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<Map<String, String>> getParameters(String layerName) {
        return s3Ops.objectStream(keyBuilder.parametersMetadataPrefix(layerName))
                .map(S3ObjectSummary::getKey)
                .map(s3Ops::getProperties)
                .map(props -> (Map<String, String>) (Map<?, ?>) props)
                .collect(Collectors.toSet());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        return s3Ops.objectStream(keyBuilder.parametersMetadataPrefix(layerName))
                .map(S3ObjectSummary::getKey)
                .map(s3Ops::getProperties)
                .map(props -> (Map<String, String>) (Map<?, ?>) props)
                .collect(Collectors.toMap(ParametersUtils::getId, Optional::of));
    }
}
