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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.util.ServletUtils;

/** A GridSubSet is a GridSet + a coverage area */
public class GridSubset {

    private final GridSet gridSet;

    // {level}{minx,miny,maxx,maxy,z}
    private final Map<Integer, GridCoverage> gridCoverageLevels;

    private final boolean fullGridSetCoverage;

    private final BoundingBox subSetExtent;

    private final Integer minCachedZoom;

    private final Integer maxCachedZoom;

    protected GridSubset(
            GridSet gridSet, Map<Integer, GridCoverage> coverages, BoundingBox originalExtent, boolean fullCoverage) {
        this(gridSet, coverages, originalExtent, fullCoverage, null, null);
    }

    public GridSubset(
            GridSet gridSet,
            Map<Integer, GridCoverage> coverages,
            BoundingBox originalExtent,
            boolean fullCoverage,
            Integer minCachedZoom,
            Integer maxCachedZoom) {
        this.gridSet = gridSet;
        this.gridCoverageLevels = coverages;
        this.subSetExtent = originalExtent;
        this.fullGridSetCoverage = fullCoverage;
        this.minCachedZoom = minCachedZoom;
        this.maxCachedZoom = maxCachedZoom;
    }

    public GridSubset(GridSubset subSet) {
        this(
                subSet.gridSet,
                new TreeMap<>(subSet.gridCoverageLevels),
                subSet.subSetExtent,
                subSet.fullGridSetCoverage,
                subSet.minCachedZoom,
                subSet.maxCachedZoom);
    }

    public BoundingBox boundsFromIndex(long[] tileIndex) {
        return getGridSet().boundsFromIndex(tileIndex);
    }

    /**
     * Finds the spatial bounding box of a rectangular group of tiles.
     *
     * @param rectangleExtent the rectangle of tiles. {minx, miny, maxx, maxy} in tile coordinates
     * @return the spatial bounding box in the coordinates of the SRS used by the GridSet
     */
    public BoundingBox boundsFromRectangle(long[] rectangleExtent) {
        return gridSet.boundsFromRectangle(rectangleExtent);
    }

    public long[] closestIndex(BoundingBox tileBounds) throws GridMismatchException {
        return gridSet.closestIndex(tileBounds);
    }

    public long[] closestRectangle(BoundingBox rectangleBounds) {
        return gridSet.closestRectangle(rectangleBounds);
    }

    /**
     * Indicates whether this gridsubset coverage contains the given tile
     *
     * @param index the tile index to check for coverage inclusion
     * @return {@code true} if {@code index} is inside this grid subset's coverage, {@code false} otherwise
     */
    public boolean covers(long[] index) {
        final int level = (int) index[2];
        final long[] coverage = getCoverage(level);
        if (coverage == null) {
            return false;
        }

        if (index[0] >= coverage[0] && index[0] <= coverage[2] && index[1] >= coverage[1] && index[1] <= coverage[3]) {
            return true;
        }

        return false;
    }

    public void checkCoverage(long[] index) throws OutsideCoverageException {
        if (covers(index)) {
            return;
        }

        if (index[2] < getZoomStart() || index[2] > getZoomStop()) {
            throw new OutsideCoverageException(index, getZoomStart(), getZoomStop());
        }

        long[] coverage = getCoverage((int) index[2]);
        throw new OutsideCoverageException(index, coverage);
    }

    public void checkTileDimensions(int width, int height) throws TileDimensionsMismatchException {

        if (width != gridSet.getTileWidth() || height != gridSet.getTileHeight()) {
            throw new TileDimensionsMismatchException(width, height, gridSet.getTileWidth(), gridSet.getTileWidth());
        }
    }

    public long[][] expandToMetaFactors(final long[][] coverages, final int[] metaFactors) {
        long[][] ret = ServletUtils.arrayDeepCopy(coverages);

        for (long[] cov : ret) {
            final int z = (int) cov[4];
            final Grid grid = this.gridSet.getGrid(z);
            final long numTilesWide = grid.getNumTilesWide();
            final long numTilesHigh = grid.getNumTilesHigh();

            cov[0] = cov[0] - (cov[0] % metaFactors[0]);
            cov[1] = cov[1] - (cov[1] % metaFactors[1]);

            cov[2] = cov[2] - (cov[2] % metaFactors[0]) + (metaFactors[0] - 1);
            if (cov[2] > numTilesWide) {
                cov[2] = numTilesWide;
            }

            cov[3] = cov[3] - (cov[3] % metaFactors[1]) + (metaFactors[1] - 1);
            if (cov[3] > numTilesHigh) {
                cov[3] = numTilesHigh;
            }
        }

        return ret;
    }

    public long[] getCoverage(int level) {
        GridCoverage gridCoverage = gridCoverageLevels.get(Integer.valueOf(level));
        if (gridCoverage == null) {
            return null;
        }
        long[] coverage = gridCoverage.coverage.clone();
        return coverage;
    }

    public long[][] getCoverages() {
        long[][] ret = new long[gridCoverageLevels.size()][5];

        final int zoomStart = getZoomStart();
        final int zoomStop = getZoomStop();

        for (int level = zoomStart, i = 0; level <= zoomStop; level++, i++) {
            long[] cov = getCoverage(level);
            ret[i] = cov;
        }

        return ret;
    }

    /** Convert pixel size to dots per inch */
    public double getDotsPerInch() {
        return (0.0254 / this.gridSet.getPixelSize());
    }

    public BoundingBox getCoverageBounds(int level) {
        long[] coverage = getCoverage(level);
        return gridSet.boundsFromRectangle(coverage);
    }

    // Returns the tightest rectangle that covers the data
    public long[] getCoverageBestFit() {
        int level;
        long[] cov = null;

        final int zoomStart = getZoomStart();
        final int zoomStop = getZoomStop();

        for (level = zoomStop; level > zoomStart; level--) {
            cov = getCoverage(level);

            if (cov[0] == cov[2] && cov[1] == cov[3]) {
                break;
            }
        }

        cov = getCoverage(level);

        return cov;
    }

    public BoundingBox getCoverageBestFitBounds() {
        return boundsFromRectangle(getCoverageBestFit());
    }

    public long[] getCoverageIntersection(long[] reqRectangle) {
        final int level = (int) reqRectangle[4];
        GridCoverage gridCov = gridCoverageLevels.get(Integer.valueOf(level));
        return gridCov.getIntersection(reqRectangle);
    }

    public long[][] getCoverageIntersections(BoundingBox reqBounds) {
        final int zoomStart = getZoomStart();
        final int zoomStop = getZoomStop();

        long[][] ret = new long[1 + zoomStop - zoomStart][5];

        for (int level = zoomStart; level <= zoomStop; level++) {
            ret[level - zoomStart] = getCoverageIntersection(level, reqBounds);
        }
        return ret;
    }

    /**
     * Find the area that covers the given rectangle with tiles from the subset.
     *
     * @param level integer zoom level at which to consider the tiles
     * @param reqBounds BoundingBox to try to cover.
     * @return Array of long, the rectangle in tile coordinates, {minx, miny, maxx, maxy}
     */
    public long[] getCoverageIntersection(int level, BoundingBox reqBounds) {
        long[] reqRectangle = gridSet.closestRectangle(level, reqBounds);
        GridCoverage gridCoverage = gridCoverageLevels.get(Integer.valueOf(level));
        return gridCoverage.getIntersection(reqRectangle);
    }

    public long getGridIndex(String gridId) {

        final int zoomStart = getZoomStart();
        final int zoomStop = getZoomStop();

        for (int index = zoomStart; index <= zoomStop; index++) {
            if (gridSet.getGrid(index).getName().equals(gridId)) {
                return index;
            }
        }

        return -1L;
    }

    public String[] getGridNames() {
        List<String> ret = new ArrayList<>(gridCoverageLevels.size());

        final int zoomStart = getZoomStart();
        final int zoomStop = getZoomStop();
        for (int i = zoomStart; i <= zoomStop; i++) {
            ret.add(gridSet.getGrid(i).getName());
        }

        return ret.toArray(new String[ret.size()]);
    }

    public GridSet getGridSet() {
        return gridSet;
    }

    public BoundingBox getGridSetBounds() {
        return gridSet.getBounds();
    }

    public long getNumTilesWide(int zoomLevel) {
        return gridSet.getGrid(zoomLevel).getNumTilesWide();
    }

    public long getNumTilesHigh(int zoomLevel) {
        return gridSet.getGrid(zoomLevel).getNumTilesHigh();
    }

    public String getName() {
        return gridSet.getName();
    }

    public BoundingBox getOriginalExtent() {
        if (this.subSetExtent == null) {
            return gridSet.getOriginalExtent();
        }
        return this.subSetExtent;
    }

    public double[] getResolutions() {
        double[] ret = new double[gridCoverageLevels.size()];

        final int zoomStart = getZoomStart();
        final int zoomStop = getZoomStop();

        for (int z = zoomStart, i = 0; z <= zoomStop; z++, i++) {
            Grid grid = gridSet.getGrid(z);
            ret[i] = grid.getResolution();
        }

        return ret;
    }

    // TODO: this is specific to KML service, move it somewhere on the kml module
    public long[][] getSubGrid(long[] gridLoc) throws GeoWebCacheException {
        final int firstLevel = getZoomStart();
        final int zoomStop = getZoomStop();
        int idx = (int) gridLoc[2];

        long[][] ret = {{-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}, {-1, -1, -1}};

        if ((idx - firstLevel + 1) <= zoomStop) {
            // Check whether this grid is doubling
            double resolutionCheck = gridSet.getGrid(idx).getResolution() / 2
                    - gridSet.getGrid(idx + 1).getResolution();

            if (Math.abs(resolutionCheck) > gridSet.getGrid(idx + 1).getResolution() * 0.025) {
                throw new GeoWebCacheException(
                        "The resolution is not decreasing by a factor of two for " + this.getName());
            } else {
                long[] coverage = getCoverage(idx + 1);

                long baseX = gridLoc[0] * 2;
                long baseY = gridLoc[1] * 2;
                long baseZ = idx + 1L;

                long[] xOffset = {0, 1, 0, 1};
                long[] yOffset = {0, 0, 1, 1};

                for (int i = 0; i < 4; i++) {
                    if (baseX + xOffset[i] >= coverage[0]
                            && baseX + xOffset[i] <= coverage[2]
                            && baseY + yOffset[i] >= coverage[1]
                            && baseY + yOffset[i] <= coverage[3]) {

                        ret[i][0] = baseX + xOffset[i];
                        ret[i][1] = baseY + yOffset[i];
                        ret[i][2] = baseZ;
                    }
                }
            }
        }

        return ret;
    }

    /** @return whether the scale is based on CRS84, even though it may not be */
    public boolean getScaleWarning() {
        return gridSet.isScaleWarning();
    }

    public SRS getSRS() {
        return gridSet.getSrs();
    }

    public int getTileHeight() {
        return gridSet.getTileHeight();
    }

    public int getTileWidth() {
        return gridSet.getTileWidth();
    }

    /**
     * WMTS is indexed from top left hand corner. We will still return {minx,miny,maxx,maxy}, but note that the y
     * positions have been reversed
     */
    // TODO: this is specific to WMTS, move it somewhere on the wmts module
    // TODO: Does this need to be public?
    public long[][] getWMTSCoverages() {
        long[][] ret = new long[gridCoverageLevels.size()][4];

        final int zoomStop = getZoomStop();
        int zoomStart = getZoomStart();
        for (int i = zoomStart; i <= zoomStop; i++) {
            Grid grid = gridSet.getGrid(i);
            long[] coverage = getCoverage(i);

            /*
             * Both internal and WMTS coordinates start at 0 and run to 1 less than the number of
             * tiles.  In internal coordinates, row 0 is the bottommost, and in WMTS it's the
             * topmost.  So subtract the row number from 1 less than the height to convert.
             */
            long bottomRow = grid.getNumTilesHigh() - 1; // The WMST row number for the bottom row

            long[] cur = {
                coverage[0], // minX
                bottomRow - coverage[3], // minY
                coverage[2], // maxX
                bottomRow - coverage[1] // maxY
            };

            ret[i - zoomStart] = cur;
        }

        return ret;
    }

    public int getZoomStart() {
        Integer firstLevel = Collections.min(gridCoverageLevels.keySet());
        return firstLevel.intValue();
    }

    public int getZoomStop() {
        Integer maxLevel = Collections.max(gridCoverageLevels.keySet());
        return maxLevel.intValue();
    }

    public Integer getMinCachedZoom() {
        return minCachedZoom;
    }

    public Integer getMaxCachedZoom() {
        return maxCachedZoom;
    }

    /** Whether the Grid Subset equals or exceeds the extent of the Grid Set */
    public boolean fullGridSetCoverage() {
        return fullGridSetCoverage;
    }

    public boolean shouldCacheAtZoom(long zoom) {
        boolean shouldCache = true;
        if (minCachedZoom != null) {
            shouldCache = zoom >= minCachedZoom;
        }
        if (shouldCache && maxCachedZoom != null) {
            shouldCache = zoom <= maxCachedZoom;
        }
        return shouldCache;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
