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
 * @author Arne OpenGeo 2010
 */

package org.geowebcache.rest.seed;

import org.geowebcache.storage.DiscontinuousTileRange;
import org.geowebcache.storage.TileRange;

public class TileRangeIterator {
    final private TileRange tr;

    final private DiscontinuousTileRange dtr;

    final private int metaX;

    final private int metaY;

    private long tilesSkippedCount = 0;

    private long tilesRenderedCount = 0;

    private long[] lastGridLoc;

    /**
     * Note that the bounds of the tile range must already be expanded to the meta tile factors for
     * this to work.
     * 
     * @param tr
     * @param metaTilingFactors
     */
    public TileRangeIterator(TileRange tr, int[] metaTilingFactors) {
        this.tr = tr;
        this.metaX = metaTilingFactors[0];
        this.metaY = metaTilingFactors[1];

        if (tr instanceof DiscontinuousTileRange) {
            dtr = (DiscontinuousTileRange) tr;
        } else {
            dtr = null;
        }
    }

    /**
     * Returns the underlying tile range
     * 
     * @return
     */
    public TileRange getTileRange() {
        return tr;
    }

    /**
     * This loops over all the possible tile locations.
     * 
     * If the TileRange object provided is a DiscontinuousTileRange implementation, each location is
     * checked against the filter of that class.
     * 
     * @return
     */
    public synchronized long[] nextMetaGridLocation() {
        long[] levelBounds;
        long x;
        long y;
        int z;

        // Figure out the starting point
        if (lastGridLoc == null) {
            z = tr.zoomStart;
            levelBounds = tr.rangeBounds[z];
            x = levelBounds[0];
            y = levelBounds[1];

        } else {
            z = (int) lastGridLoc[2];
            levelBounds = tr.rangeBounds[z];
            x = lastGridLoc[0] + metaX;
            y = lastGridLoc[1];
        }

        try {
            // Loop over any remaining zoom levels
            for (; z <= tr.zoomStop; z++) {
                for (; y < levelBounds[3]; y += metaY) {
                    for (; x < levelBounds[2]; x += metaX) {

                        long[] gridLoc = { x, y, z };

                        int tileCount = tilesForLocation(gridLoc, levelBounds);

                        if (checkGridLocation(gridLoc)) {
                            tilesRenderedCount += tileCount;
                            lastGridLoc = gridLoc.clone();
                            return gridLoc;
                        }

                        tilesSkippedCount += tileCount;
                    }
                    x = levelBounds[0];
                }

                // Get ready for the next level
                if (z < tr.zoomStop) {// but be careful not to go out of index
                    levelBounds = tr.rangeBounds[z + 1];
                    x = levelBounds[0];
                    y = levelBounds[1];
                }
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Calculates the number of tiles covered by the meta tile for this grid location.
     * 
     * @param gridLoc
     * @param levelBounds
     * @return
     */
    private int tilesForLocation(long[] gridLoc, long[] levelBounds) {
        return (int) Math.min(metaX, levelBounds[2] - gridLoc[0])
                * (int) Math.min(metaY, levelBounds[3] - gridLoc[1]);
    }

    /**
     * Checks whether this grid location, or any on the same meta tile, should be included according
     * to the DiscontinuousTileRange
     * 
     * @param gridLoc
     * @return
     */
    private boolean checkGridLocation(long[] gridLoc) {
        if (dtr == null) {
            return true;
        } else {
            for (int i = 0; i < this.metaX; i++) {
                for (int j = 0; j < this.metaY; j++) {
                    long[] subIdx = { gridLoc[0] + i, gridLoc[1] + j, gridLoc[2] };

                    if (dtr.contains(subIdx)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * The number of tiles this iterator has skipped so far.
     */
    public synchronized long getCountSkipped() {
        return tilesSkippedCount;
    }

    /**
     * The number of tiles for which this iterator has returned a grid location.
     */
    public synchronized long getCountRendered() {
        return tilesRenderedCount;
    }
}
