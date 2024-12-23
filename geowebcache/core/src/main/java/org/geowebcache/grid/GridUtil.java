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
package org.geowebcache.grid;

import java.util.List;

public class GridUtil {

    /**
     * @return null if none matches, the gridset with a tile index closest to the requested bounds and dimensions
     *     otherwise
     */
    public static GridSubset findBestMatchingGrid(
            final BoundingBox reqBounds,
            final List<GridSubset> crsMatchingGridSubsets,
            final Integer expectedTileWidth,
            final Integer expectedTileHeight,
            long[] matchingTileIndexTarget) {

        GridSubset bestMatch = null;
        long[] bestMatchingTileIndex = null;

        for (GridSubset crsMatch : crsMatchingGridSubsets) {
            try {
                final int tileWidth = crsMatch.getTileWidth();
                final int tileHeight = crsMatch.getTileHeight();
                if ((expectedTileWidth != null && expectedTileWidth.intValue() != tileWidth)
                        || (expectedTileHeight != null && expectedTileHeight.intValue() != tileHeight)) {
                    // don't even consider it
                    continue;
                }
                final long[] matchingTileIndex = crsMatch.closestIndex(reqBounds);
                if (bestMatch == null) {
                    bestMatch = crsMatch;
                    bestMatchingTileIndex = matchingTileIndex;
                    continue;
                }

                BoundingBox previousBounds = bestMatch.boundsFromIndex(bestMatchingTileIndex);
                BoundingBox bounds = crsMatch.boundsFromIndex(matchingTileIndex);

                final double reqArea = reqBounds.getWidth() * reqBounds.getHeight();
                final double previousArea = previousBounds.getWidth() * previousBounds.getHeight();
                final double currArea = bounds.getWidth() * bounds.getHeight();

                double deltaPrev = Math.abs(reqArea - previousArea);
                double deltaCurr = Math.abs(reqArea - currArea);

                if (deltaCurr < deltaPrev) {
                    bestMatch = crsMatch;
                    bestMatchingTileIndex = matchingTileIndex;
                }

            } catch (GridMismatchException doesNotMatch) {
                continue;
            }
        }
        if (bestMatchingTileIndex != null) {
            System.arraycopy(bestMatchingTileIndex, 0, matchingTileIndexTarget, 0, 3);
        }
        return bestMatch;
    }
}
