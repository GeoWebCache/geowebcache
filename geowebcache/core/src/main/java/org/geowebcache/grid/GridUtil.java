package org.geowebcache.grid;

import java.util.List;

public class GridUtil {

    /**
     * @param reqBounds
     * @param crsMatchingGridSubsets
     * @param expectedTileWidth
     * @param expectedTileHeight
     * @param matchingTileIndexTarget
     * @return null if none matches, the gridset with a tile index closest to the requested bounds
     *         and dimensions otherwise
     */
    public static GridSubset findBestMatchingGrid(final BoundingBox reqBounds,
            final List<GridSubset> crsMatchingGridSubsets, final Integer expectedTileWidth,
            final Integer expectedTileHeight, long[] matchingTileIndexTarget) {

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
