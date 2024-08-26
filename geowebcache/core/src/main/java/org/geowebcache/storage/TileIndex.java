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
 * <p>Copyright 2024
 */
package org.geowebcache.storage;

import java.util.Objects;

public interface TileIndex {

    long getX();

    long getY();

    int getZ();

    default boolean isSameTileIndex(TileIndex index) {
        return null != index
                && getX() == index.getX()
                && getY() == index.getY()
                && getZ() == index.getZ();
    }

    static TileIndex newInstance() {
        return new DefaultTileIndex();
    }

    static TileIndex valueOf(long[] tileIndex) {
        return valueOf(tileIndex[0], tileIndex[1], (int) tileIndex[2]);
    }

    static TileIndex valueOf(long x, long y, int z) {
        return new DefaultTileIndex(x, y, z);
    }

    static TileIndex copyOf(TileIndex index) {
        return valueOf(index.getX(), index.getY(), index.getZ());
    }

    static class DefaultTileIndex implements TileIndex {
        private long x;
        private long y;
        private int z;

        DefaultTileIndex() {}

        DefaultTileIndex(long x, long y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public long getX() {
            return x;
        }

        @Override
        public long getY() {
            return y;
        }

        @Override
        public int getZ() {
            return z;
        }

        @Override
        public String toString() {
            return TileIndex.toString(this);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            DefaultTileIndex other = (DefaultTileIndex) obj;
            return x == other.x && y == other.y && z == other.z;
        }
    }

    static String toString(TileIndex index) {
        return String.format("[%d, %d, %d]", index.getX(), index.getY(), index.getZ());
    }
}
