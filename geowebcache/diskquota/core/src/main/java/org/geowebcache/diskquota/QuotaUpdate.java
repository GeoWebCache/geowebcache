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

import org.geowebcache.diskquota.storage.TileSet;

public class QuotaUpdate {

    private final TileSet tileSet;

    private long size;

    private long[] tileIndex;

    /** @param size bytes to add or subtract from a quota: positive value increase quota, negative value decreases it */
    public QuotaUpdate(
            String layerName, String gridsetId, String blobFormat, String parametersId, long size, long[] tileIndex) {
        this(new TileSet(layerName, gridsetId, blobFormat, parametersId), size, tileIndex);
    }

    public QuotaUpdate(TileSet tileset, long quotaUpdateSize, long[] tileIndex) {
        this.tileSet = tileset;
        this.size = quotaUpdateSize;
        this.tileIndex = tileIndex;
    }

    public TileSet getTileSet() {
        return tileSet;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long[] getTileIndex() {
        return tileIndex;
    }

    @Override
    public String toString() {
        return new StringBuilder("[")
                .append(tileSet.toString())
                .append(", ")
                .append(size)
                .append(" bytes]")
                .toString();
    }
}
