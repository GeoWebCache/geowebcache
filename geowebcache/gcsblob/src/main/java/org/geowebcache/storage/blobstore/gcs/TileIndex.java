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

/**
 * A record to hold the spatial coordinates of a tile in a tile matrix set.
 *
 * <p>This record represents the position of a tile within a gridset using the standard TMS (Tile Map Service)
 * coordinate system. The coordinates follow the convention where:
 *
 * <ul>
 *   <li>{@code x} - the column index of the tile (west to east)
 *   <li>{@code y} - the row index of the tile (south to north in TMS, north to south in some other systems)
 *   <li>{@code z} - the zoom level, where higher values represent higher zoom levels (more detail)
 * </ul>
 *
 * <p>TileIndex instances are typically created from a {@link org.geowebcache.storage.TileRange} using
 * {@link org.geowebcache.storage.TileRangeIterator} to iterate over all tiles in a range. See
 * {@link GoogleCloudStorageBlobStore#toTileIndices(org.geowebcache.storage.TileRange)}.
 *
 * @param x The column index of the tile (horizontal position).
 * @param y The row index of the tile (vertical position).
 * @param z The zoom level of the tile.
 * @see TileLocation
 * @see CacheId
 * @since 1.28
 */
record TileIndex(long x, long y, int z) {}
