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
 * Copyright 2018
 *
 */
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
