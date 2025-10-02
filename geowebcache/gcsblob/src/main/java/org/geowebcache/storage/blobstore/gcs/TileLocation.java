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

import org.geowebcache.storage.TileObject;
import org.geowebcache.util.TMSKeyBuilder;
import org.springframework.util.StringUtils;

/**
 * A record to hold the complete location information for a tile in Google Cloud Storage.
 *
 * <p>This record combines all the components necessary to uniquely identify and locate a tile in a blob store:
 *
 * <ul>
 *   <li>{@code prefix} - the storage prefix path (bucket-level prefix for organizing caches)
 *   <li>{@code cache} - a {@link CacheId} that identifies the layer, gridset, format, and parameters
 *   <li>{@code tile} - a {@link TileIndex} that identifies the tile's spatial coordinates
 * </ul>
 *
 * <p>TileLocation instances are typically created from a {@link org.geowebcache.storage.TileRange} by combining layer
 * information into a {@link CacheId}, extracting tile coordinates into {@link TileIndex} instances, and adding the
 * storage prefix. See {@link GoogleCloudStorageBlobStore#toTileLocations(org.geowebcache.storage.TileRange)}.
 *
 * <p>The primary purposes of this record are:
 *
 * <ul>
 *   <li>Generate the complete storage key path for a tile using {@link #getStorageKey()}, which follows the TMS (Tile
 *       Map Service) key structure
 *   <li>Provide a convenient way to pass complete tile information to callbacks during bulk operations, such as in
 *       {@link GoogleCloudStorageBlobStore#sendTileDeleted(TileLocation, long)}, where all tile metadata is extracted
 *       and forwarded to blob store listeners
 * </ul>
 *
 * @param prefix The storage prefix path, can be {@code null} or empty.
 * @param cache The cache identity containing layer, gridset, format, and parameters information.
 * @param tile The tile's spatial coordinates (x, y, z).
 * @see CacheId
 * @see TileIndex
 * @see TMSKeyBuilder
 * @since 1.28
 */
record TileLocation(String prefix, CacheId cache, TileIndex tile) {

    /**
     * Same as {@link TMSKeyBuilder#forTile(TileObject)} but using this record's data
     *
     * @return {@code <prefix>/<layer name>/<gridset id>/<format id>/<parametersId>/<z>/<x>/<y>.<extension>}
     */
    public String getStorageKey() {
        String parametersId = cache.parametersId();
        if (parametersId == null) {
            parametersId = "default";
        }
        String layerId = cache.layerId();
        String gridset = cache.gridsetId();
        String shortFormat = cache.format().getFileExtension();
        String extension = cache.format().getInternalName(); // png, jpeg, etc.

        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(prefix)) {
            sb.append(prefix).append('/');
        }

        sb.append(layerId)
                .append('/')
                .append(gridset)
                .append('/')
                .append(shortFormat)
                .append('/')
                .append(parametersId)
                .append('/')
                .append(tile.z())
                .append('/')
                .append(tile.x())
                .append('/')
                .append(tile.y())
                .append('.')
                .append(extension);

        return sb.toString();
    }
}
