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
 * @author Gabriel Roldan, Camptocamp, Copyright 2025
 */
package org.geowebcache.storage.blobstore.gcs;

import org.geowebcache.mime.MimeType;

/**
 * A record to hold the components of a tile's cache identity.
 *
 * <p>Note {@code layerName} is used to provide the layer name to callbacks (see
 * {@link GoogleCloudStorageBlobStore#sendTileDeleted(TileLocation, long)}, may the layer id be different than the layer
 * name like in GeoServer tile layers. For all other purposes, {@code layerId} uniquely identifies the layer (e.g. for
 * layer cache prefixes)
 *
 * @param layerId The unique identifier of the layer.
 * @param layerName The name of the layer.
 * @param gridsetId The identifier of the gridset.
 * @param format The MIME type of the tile.
 * @param parametersId The identifier for the tile's parameter set, can be {@code null}.
 * @since 1.28
 */
record CacheId(String layerId, String layerName, String gridsetId, MimeType format, String parametersId) {}
