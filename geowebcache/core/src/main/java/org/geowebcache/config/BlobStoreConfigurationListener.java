package org.geowebcache.config;

import org.geowebcache.GeoWebCacheException;

import java.io.IOException;

/**
 * Indicates a class should listen to {@link BlobStoreConfiguration} change events.
 * Implementations of this class are responsible for registering themselves via
 * {@link org.geowebcache.storage.BlobStoreAggregator}
 */
public interface BlobStoreConfigurationListener {
    void handleAddBlobStore(BlobStoreInfo newBlobStore) throws GeoWebCacheException, IOException;

    void handleRemoveBlobStore(BlobStoreInfo removedBlobStore) throws GeoWebCacheException, IOException;

    void handleModifyBlobStore(BlobStoreInfo modifiedBlobStore) throws GeoWebCacheException, IOException;

    void handleRenameBlobStore(String oldName, BlobStoreInfo modifiedBlobStore) throws GeoWebCacheException, IOException;
}
