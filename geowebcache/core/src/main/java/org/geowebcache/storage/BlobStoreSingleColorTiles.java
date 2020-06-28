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
package org.geowebcache.storage;

import java.io.IOException;
import java.util.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Manages the persistence of the actual data contained in cacheable objects (tiles, WFS responses).
 * This is an extension to support single colour tile caching.
 *
 * <p>Blobstores may assume that the StorageObjects passed to them are completely filled in except
 * for the blob fields.
 */
public interface BlobStoreSingleColorTiles extends BlobStore {
    static Log log = LogFactory.getLog(BlobStoreSingleColorTiles.class);

    /**
     * Creates a symlink.
     *
     * @param tileObject The tile object.
     * @param singleColourTileRef The tile colour reference.
     */
    public void putSymlink(TileObject tileObject, String singleColourTileRef);

    /**
     * Checks if a tile exists in storage given a tile name.
     *
     * @param singleColourTileRef The tile colour reference.
     * @return If the tile exists in storage.
     */
    public boolean tileExistsInStorage(TileObject tile, String singleColourTileRef);

    /**
     * Saves a single colour tile. This is different from put as there is a different filepath
     * defined for single color tiles.
     *
     * @param tile The tile object.
     * @param singleColourTileRef The tile colour reference.
     * @throws IOException
     */
    public void saveSingleColourTile(TileObject tile, String singleColourTileRef)
            throws IOException;
}
