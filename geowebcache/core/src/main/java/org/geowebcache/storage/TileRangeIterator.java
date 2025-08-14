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
 * @author Arne OpenGeo 2010
 */
package org.geowebcache.storage;

import java.util.concurrent.atomic.AtomicLong;

public class TileRangeIterator {

    private final TileRange tr;

    private final DiscontinuousTileRange dtr;

    private final int metaX;

    private final int metaY;

    private AtomicLong tilesSkippedCount = new AtomicLong();

    private AtomicLong tilesRenderedCount = new AtomicLong();

    private volatile long[] lastGridLoc;

    /** Note that the bounds of the tile range must already be expanded to the meta tile factors for this to work. */
    public TileRangeIterator(TileRange tr, int[] metaTilingFactors) {
        this.tr = tr;
        this.metaX = metaTilingFactors[0];
        this.metaY = metaTilingFactors[1];

        if (tr instanceof DiscontinuousTileRange range) {
            dtr = range;
        } else {
            dtr = null;
        }
    }

    /** Returns the underlying tile range */
    public TileRange getTileRange() {
        return tr;
    }

    /**
     * This loops over all the possible metatile locations and returns a tile location within each metatile.
     *
     * <p>If the TileRange object provided is a DiscontinuousTileRange implementation, each location is checked against
     * the filter of that class.
     *
     * @param gridLoc as an optimization, re-use the previous gridLoc. It will be changed and used as the return value.
     *     The values passed in will not impact the result. For the first call, use a new 3 element array.
     * @return {@code null} if there're no more tiles to return, the next grid location in the iterator otherwise. The
     *     array has three elements: {x,y,z}
     */
    public synchronized long[] nextMetaGridLocation(final long[] gridLoc) {
        long[] levelBounds;
        long x;
        long y;
        int z;

        // Figure out the starting point
        if (lastGridLoc == null) {
            z = tr.getZoomStart();
            levelBounds = tr.rangeBounds(z);
            x = levelBounds[0];
            y = levelBounds[1];

        } else {
            z = (int) lastGridLoc[2];
            levelBounds = tr.rangeBounds(z);
            x = lastGridLoc[0] + metaX;
            y = lastGridLoc[1];
        }

        // Loop over any remaining zoom levels
        for (; z <= tr.getZoomStop(); z++) {
            for (; y <= levelBounds[3]; y += metaY) {
                for (; x <= levelBounds[2]; x += metaX) {

                    gridLoc[0] = x;
                    gridLoc[1] = y;
                    gridLoc[2] = z;

                    int tileCount = tilesForLocation(gridLoc, levelBounds);

                    if (checkGridLocation(gridLoc)) {
                        tilesRenderedCount.addAndGet(tileCount);
                        lastGridLoc = gridLoc.clone();
                        return gridLoc;
                    }

                    tilesSkippedCount.addAndGet(tileCount);
                }
                x = levelBounds[0];
            }

            // Get ready for the next level
            if (z < tr.getZoomStop()) { // but be careful not to go out of index
                levelBounds = tr.rangeBounds(z + 1);
                x = levelBounds[0];
                y = levelBounds[1];
            }
        }

        return null;
    }

    /** Calculates the number of tiles covered by the meta tile for this grid location. */
    private int tilesForLocation(long x, long y, long[] levelBounds) {
        long boundsMaxX = levelBounds[2];
        long boundsMaxY = levelBounds[3];
        return (int) Math.min(metaX, 1 + (boundsMaxX - x)) * (int) Math.min(metaY, 1 + (boundsMaxY - y));
    }

    private int tilesForLocation(long[] gridLoc, long[] levelBounds) {
        return tilesForLocation(gridLoc[0], gridLoc[1], levelBounds);
    }

    /**
     * Checks whether this grid location, or any on the same meta tile, should be included according to the
     * DiscontinuousTileRange
     */
    private boolean checkGridLocation(long[] gridLoc) {
        if (dtr == null) {
            return true;
        } else {
            long[] subIdx = new long[3];
            subIdx[2] = gridLoc[2];
            for (int i = 0; i < this.metaX; i++) {
                for (int j = 0; j < this.metaY; j++) {
                    subIdx[0] = gridLoc[0] + i;
                    subIdx[1] = gridLoc[1] + j;
                    if (dtr.contains(subIdx)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
