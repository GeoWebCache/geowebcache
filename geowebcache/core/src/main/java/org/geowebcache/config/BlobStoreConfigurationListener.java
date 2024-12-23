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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import java.io.IOException;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.storage.UnsuitableStorageException;

/**
 * Indicates a class should listen to {@link BlobStoreConfiguration} change events. Implementations of this class are
 * responsible for registering themselves via {@link org.geowebcache.storage.BlobStoreAggregator}
 */
public interface BlobStoreConfigurationListener {

    /**
     * @param newBlobStore The configuration for the blobstore that was added
     * @throws UnsuitableStorageException The blobstore is attempting to use an illegal storage location and the change
     *     should be rolled back.
     */
    void handleAddBlobStore(BlobStoreInfo newBlobStore)
            throws UnsuitableStorageException, GeoWebCacheException, IOException;

    /** @param removedBlobStore The old configuration for the blobstore that was removed */
    void handleRemoveBlobStore(BlobStoreInfo removedBlobStore) throws GeoWebCacheException, IOException;

    /**
     * @param modifiedBlobStore The new configuration for the blobstore
     * @throws UnsuitableStorageException The blobstore is attempting to use an illegal storage location and the change
     *     should be rolled back.
     */
    void handleModifyBlobStore(BlobStoreInfo modifiedBlobStore)
            throws UnsuitableStorageException, GeoWebCacheException, IOException;

    /**
     * @param oldName The old name of the blobstore
     * @param modifiedBlobStore The configuration for the blobstore including its new name
     */
    void handleRenameBlobStore(String oldName, BlobStoreInfo modifiedBlobStore)
            throws GeoWebCacheException, IOException;
}
