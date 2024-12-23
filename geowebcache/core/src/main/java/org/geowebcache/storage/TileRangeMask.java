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
 * <p>Copyright 2014
 */
package org.geowebcache.storage;

import org.geowebcache.seed.GWCTask;

/**
 * A mask applied to a {@link DiscontinuousTileRange} to limit which tiles are seeded/truncated out of the range's full
 * coverage by means of a {@link TileRangeIterator}.
 *
 * @see RasterMask
 * @see TileRangeIterator
 * @see GWCTask
 */
public interface TileRangeMask {

    public abstract long[][] getGridCoverages();

    public abstract boolean lookup(long x, long y, int z);
}
