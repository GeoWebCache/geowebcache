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
 * @author Dana Lambert, Catalyst IT Ltd NZ, Copyright 2020
 */
package org.geowebcache.swift;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.jclouds.io.Payloads.newInputStreamPayload;

import com.google.common.base.Function;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.*;
import javax.annotation.Nullable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.*;
import org.jclouds.http.HttpResponseException;
import org.jclouds.io.MutableContentMetadata;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.BaseMutableContentMetadata;
import org.jclouds.openstack.swift.v1.SwiftApi;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedBlobStoreContext;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedSwiftBlobStore;
import org.jclouds.openstack.swift.v1.domain.SwiftObject;
import org.jclouds.openstack.swift.v1.features.BulkApi;
import org.jclouds.openstack.swift.v1.features.ObjectApi;
import org.jclouds.openstack.swift.v1.options.ListContainerOptions;

/** Blobstore class compatatble with Openstack Swift. */
public class SwiftBlobStore implements BlobStore {

    static Log log = LogFactory.getLog(SwiftBlobStore.class);

    private final BlobStoreListenerList listeners = new BlobStoreListenerList();

    private String containerName;

    private final TMSKeyBuilder keyBuilder;

    private volatile boolean shutDown;

    /** JClouds Swift API */
    private SwiftApi swiftApi;

    /** Swift Object API */
    private ObjectApi objectApi;

    /** Swift Bulk API */
    private BulkApi bulkApi;

    private RegionScopedBlobStoreContext blobStoreContext;

    private RegionScopedSwiftBlobStore blobStore;

    public SwiftBlobStore(SwiftBlobStoreInfo config, TileLayerDispatcher layers) {

        checkNotNull(config);
        checkNotNull(layers);

        String prefix = config.getPrefix() == null ? "" : config.getPrefix();
        this.keyBuilder = new TMSKeyBuilder(prefix, layers);
        this.containerName = config.getContainer();

        swiftApi = config.buildApi();
        objectApi = swiftApi.getObjectApi(config.getRegion(), containerName);
        bulkApi = this.swiftApi.getBulkApi(config.getRegion());

        blobStoreContext = config.getBlobStore();
        blobStore = (RegionScopedSwiftBlobStore) blobStoreContext.getBlobStore(config.getRegion());
    }

    @Override
    public void destroy() {
        try {
            this.shutDown = true;
            this.swiftApi.close();
            this.blobStoreContext.close();
        } catch (IOException e) {
            log.error("Error closing connection.");
            log.error(e);
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
        checkNotNull(blob, "Object Blob is null");

        MutableContentMetadata contentMetadata = new BaseMutableContentMetadata();

        // Set content length
        final String key = keyBuilder.forTile(obj);
        contentMetadata.setContentLength(blob.getSize());

        // Set content type
        checkNotNull(obj.getBlobFormat(), "Object Blob Format is null");
        String blobFormat = obj.getBlobFormat();
        String mimeType;
        try {
            mimeType = MimeType.createFromFormat(blobFormat).getMimeType();
        } catch (MimeException me) {
            throw new RuntimeException(me);
        }
        contentMetadata.setContentType(mimeType);

        // don't bother for the extra call if there are no listeners
        final boolean existed;
        SwiftObject oldObj;
        if (listeners.isEmpty()) {
            existed = false;
            oldObj = null;
        } else {
            oldObj = this.objectApi.get(key);
            existed = oldObj != null;
        }

        log.trace("Storing " + obj.getLayerName());

        // Upload the payload to object storage
        try {
            Payload payload = newInputStreamPayload(blob.getInputStream());
            payload.setContentMetadata(contentMetadata);

            // Retry logic in case upload fails
            long failures = 0;
            boolean retry = true;
            while (retry && failures < 3) {
                try {
                    this.objectApi.put(key, payload);
                    retry = false;
                } catch (HttpResponseException e) {
                    failures += 1;
                } finally {
                    payload.close();
                }
            }

            if (failures > 0) {
                log.debug("Some attempts to upload layer " + obj.getLayerName() + " have failed.");
                log.debug("Failures count: " + failures);
            }
        } catch (IOException e) {
            log.debug("Error getting payload.");
            log.debug(e.getMessage());
        }

        // This is important because listeners may be tracking tile existence
        if (!listeners.isEmpty()) {
            if (existed) {
                long oldSize = oldObj.getMetadata().size(); // Assuming this is correct
                listeners.sendTileUpdated(obj, oldSize);
            } else {
                listeners.sendTileStored(obj);
            }
        }
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        final String key = keyBuilder.forTile(obj);
        SwiftObject object = this.objectApi.get(key);

        if (object == null) {
            return false;
        }

        try (Payload in = object.getPayload()) {
            byte[] bytes = ByteStreams.toByteArray(in.openStream());
            obj.setBlobSize(bytes.length);
            obj.setBlob(new ByteArrayResource(bytes));
            obj.setCreated(object.getLastModified().getTime());
        } catch (IOException e) {
            throw new StorageException("Error getting " + key, e);
        }

        return true;
    }

    private class TileToKey implements Function<long[], String> {

        private final String coordsPrefix;

        private final String extension;

        public TileToKey(String coordsPrefix, MimeType mimeType) {
            this.coordsPrefix = coordsPrefix;
            this.extension = mimeType.getInternalName();
        }

        @Override
        public String apply(long[] loc) {
            long z = loc[2];
            long x = loc[0];
            long y = loc[1];
            StringBuilder sb = new StringBuilder(coordsPrefix);

            // builds the path to the tile
            sb.append(z).append('/').append(x).append('/').append(y).append('.').append(extension);
            return sb.toString();
        }
    }

    @Override
    public boolean delete(final TileRange tileRange) throws StorageException {

        final String coordsPrefix = keyBuilder.coordinatesPrefix(tileRange);

        if (this.objectApi.get(coordsPrefix) == null) {
            return false;
        }

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

        if (listeners.isEmpty()) {

            // if there are no listeners, don't bother requesting every tile
            // metadata to notify the listeners
            Iterator<List<long[]>> partition =
                    Iterators.partition(
                            tileLocations, // Iterator
                            1000); // this breaks a list into a lump of 1000s of items per sublist

            final TileToKey tileToKey = new TileToKey(coordsPrefix, tileRange.getMimeType());

            while (partition.hasNext() && !shutDown) {
                List<long[]> locations = partition.next();
                List<String> keys = Lists.transform(locations, tileToKey);
                this.bulkApi.bulkDelete(keys);
            }

        } else {
            long[] xyz;
            String layerName = tileRange.getLayerName();
            String gridSetId = tileRange.getGridSetId();
            String format = tileRange.getMimeType().getFormat();
            Map<String, String> parameters = tileRange.getParameters();

            while (tileLocations.hasNext()) {
                xyz = tileLocations.next();

                TileObject tile =
                        TileObject.createQueryTileObject(
                                layerName, xyz, gridSetId, format, parameters);
                tile.setParametersId(tileRange.getParametersId());

                // Delete each tile object in the range given
                this.delete((TileObject) tile);
            }
        }
        return true;
    }

    @Override
    public boolean delete(String layerName) throws StorageException {

        checkNotNull(layerName, "layerName");

        boolean deletionSuccessful = this.deleteByPath(layerName);

        // If the layer has been deleted successfully update the listeners
        if (deletionSuccessful) {
            listeners.sendLayerDeleted(layerName);
        }

        return deletionSuccessful;
    }

    @Override
    public boolean deleteByGridsetId(final String layerName, final String gridSetId)
            throws StorageException {
        checkNotNull(layerName, "layerName");
        checkNotNull(gridSetId, "gridSetId");

        final String gridsetPrefix = keyBuilder.forGridset(layerName, gridSetId);

        boolean deletedSuccesfully = this.deleteByPath(gridsetPrefix);

        // If the layer has been deleted successfuly update the listeners
        if (deletedSuccesfully) {
            listeners.sendGridSubsetDeleted(layerName, gridSetId);
        }

        // return if the layer has been succesfully deleted
        return deletedSuccesfully;
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        final String objName = obj.getLayerName();

        checkNotNull(objName, "Object Name");

        // don't bother for the extra call if there are no listeners
        if (listeners.isEmpty()) {
            return this.deleteByPath(objName);
        }

        boolean deleted = this.deleteByPath(objName);

        if (deleted) {
            listeners.sendTileDeleted(obj);
        }

        return deleted;
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        log.debug("No need to rename layers, SwiftBlobStore uses layer id as key root");
        if (objectApi.get(oldLayerName) != null) {
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
        SwiftObject layer = this.objectApi.get(layerName);

        if (layer == null) {
            return null;
        }

        if (layer.getMetadata() == null) {
            return null;
        } else {
            return layer.getMetadata().get(key);
        }
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        SwiftObject layer = this.objectApi.get(layerName);
        Map<String, String> metaData;

        if (layer == null) {
            return;
        }

        metaData = layer.getMetadata();
        if (metaData == null) {
            metaData = new HashMap<>();
        }

        metaData.put(key, value);
        this.objectApi.updateMetadata(layerName, metaData);
    }

    @Override
    public boolean layerExists(String layerName) {
        return this.objectApi.get(layerName) != null;
    }

    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        String prefix = keyBuilder.parametersMetadataPrefix(layerName);
        ListContainerOptions options = new ListContainerOptions();
        options.prefix(prefix);

        Map<String, Optional<Map<String, String>>> paramMapping = new HashMap<>();
        for (SwiftObject obj : this.objectApi.list(options)) {
            paramMapping.put(obj.getName(), Optional.of(obj.getMetadata()));
        }

        return paramMapping;
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId)
            throws StorageException {
        checkNotNull(layerName, "layerName");
        checkNotNull(parametersId, "parametersId");

        boolean deletionSuccessful =
                keyBuilder
                        // Gets some parameters - probably some iterable
                        .forParameters(layerName, parametersId)
                        // Creates a stream from this data
                        .stream()
                        // Maps the stream to whether the object has been successfully deleted.
                        .map(
                                path -> {
                                    return this.deleteByPath(path);
                                })
                        // Checks if all the entries were true - meaning everything was deleted
                        // successfully.
                        .reduce(Boolean::logicalAnd)
                        // If reduce is not successful then return false.
                        .orElse(false);

        // If deletion was successful then tell the listeners.
        if (deletionSuccessful) {
            listeners.sendParametersDeleted(layerName, parametersId);
        }

        return deletionSuccessful;
    }

    protected boolean deleteByPath(String path) {

        org.jclouds.blobstore.options.ListContainerOptions options =
                new org.jclouds.blobstore.options.ListContainerOptions();
        options.prefix(path);
        options.recursive();

        this.blobStore.clearContainer(this.containerName, options);

        // NOTE: this is messy but it seems to work.
        // there might be a more effecient way of doing this.
        return this.blobStore.list(this.containerName, options).isEmpty();
    }
}
