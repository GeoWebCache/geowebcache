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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.grid;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

public class GridSubsetFactory {
    private static Logger log = Logging.getLogger(GridSubsetFactory.class.getName());

    public static GridSubset createGridSubSet(GridSet gridSet) {

        GridSubset ret = createGridSubSet(gridSet, gridSet.getOriginalExtent(), 0, gridSet.getNumLevels() - 1);
        return ret;
    }

    public static GridSubset createGridSubSet(
            GridSet gridSet, BoundingBox extent, Integer zoomStart, Integer zoomStop) {
        return createGridSubSet(gridSet, extent, zoomStart, zoomStop, null, null);
    }

    public static GridSubset createGridSubSet(
            GridSet gridSet,
            BoundingBox extent,
            Integer zoomStart,
            Integer zoomStop,
            Integer minCachedZoom,
            Integer maxCachedZoom) {

        if (gridSet == null) {
            throw new NullPointerException("Passed GridSet was null!");
        }

        final int maxLevel = gridSet.getNumLevels() - 1;
        if (zoomStart == null) {
            zoomStart = 0;
        }
        if (zoomStop == null) {
            zoomStop = maxLevel;
        } else if (zoomStop > maxLevel) {
            String message = "Requested to create GridSubset with zoomStop "
                    + zoomStop
                    + " for GridSet "
                    + gridSet.getName()
                    + " whose max zoom level is "
                    + maxLevel
                    + ". Limiting GridSubset to zoomStop = "
                    + maxLevel;
            log.warning(message);
            zoomStop = maxLevel;
        }

        Map<Integer, GridCoverage> coverages = new TreeMap<>();
        for (int z = zoomStart; z <= zoomStop; z++) {

            Grid level = gridSet.getGrid(z);

            long[] coverage;
            if (extent == null) {
                long maxColX = level.getNumTilesWide() - 1;
                long maxColY = level.getNumTilesHigh() - 1;
                coverage = new long[] {0, 0, maxColX, maxColY, z};
            } else {
                coverage = gridSet.closestRectangle(z, extent);
            }

            GridCoverage gridCov = new GridCoverage(coverage);
            coverages.put(Integer.valueOf(z), gridCov);
        }

        // Save the original extent provided by the user
        BoundingBox originalExtent = extent;
        boolean fullCoverage = false;

        // Is this plain wrong? GlobalCRS84Scale, I guess the resolution forces it
        BoundingBox gridSetBounds = gridSet.getBounds();
        if (extent == null || extent.contains(gridSetBounds)) {
            fullCoverage = true;
            originalExtent = gridSetBounds;
        }

        GridSubset ret = new GridSubset(gridSet, coverages, originalExtent, fullCoverage, minCachedZoom, maxCachedZoom);
        return ret;
    }
}
