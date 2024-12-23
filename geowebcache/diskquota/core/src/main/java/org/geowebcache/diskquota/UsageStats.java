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
 * <p>Copyright 2019
 */
package org.geowebcache.diskquota;

import java.util.Arrays;
import org.geowebcache.diskquota.storage.TileSet;

public class UsageStats {

    private final TileSet tileSet;

    private final long[] tileIndex;

    public UsageStats(TileSet tileset, long[] tileIndex) {
        this.tileSet = tileset;
        this.tileIndex = tileIndex;
    }

    public TileSet getTileSet() {
        return tileSet;
    }

    public long[] getTileIndex() {
        return tileIndex;
    }

    @Override
    public String toString() {
        return new StringBuilder("[")
                .append(tileSet.toString())
                .append(", ")
                .append(Arrays.toString(tileIndex))
                .append("]")
                .toString();
    }
}
