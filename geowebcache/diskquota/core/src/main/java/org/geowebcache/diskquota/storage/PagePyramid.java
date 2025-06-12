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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota.storage;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Map;
import java.util.TreeMap;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.storage.TileRange;
import org.springframework.util.Assert;

/**
 * Pyramid of tile pages for a given {@link GridSubset}
 *
 * <p>This is a support class for {@link TilePageCalculator}, hence package visible.
 *
 * @author groldan
 */
class PagePyramid {

    /** {@code [level][numTilesPerPageX, numTilesPerPageY, numPagesX, numPagesY]} */
    private Map<Integer, PageLevelInfo> pageInfo;

    private Map<Integer, long[]> gridSubsetCoverages;

    private final int zoomStart;

    private final int zoomStop;

    public static final class PageLevelInfo {

        public final int pagesX;

        public final int pagesY;

        public final int tilesPerPageX;

        public final int tilesPerPageY;

        public final BigInteger tilesPerPage;

        public final long coverageMinX;

        public final long coverageMinY;

        public final long coverageMaxX;

        public final long coverageMaxY;

        public PageLevelInfo(
                int pagesX,
                int pagesY,
                int tilesPerPageX,
                int tilesPerPageY,
                long coverageMinX,
                long coverageMinY,
                long coverageMaxX,
                long coverageMaxY) {
            this.pagesX = pagesX;
            this.pagesY = pagesY;
            this.tilesPerPageX = tilesPerPageX;
            this.tilesPerPageY = tilesPerPageY;
            this.tilesPerPage = BigInteger.valueOf(tilesPerPageX).multiply(BigInteger.valueOf(tilesPerPageY));
            this.coverageMinX = coverageMinX;
            this.coverageMinY = coverageMinY;
            this.coverageMaxX = coverageMaxX;
            this.coverageMaxY = coverageMaxY;
        }

        @Override
        public String toString() {
            NumberFormat nf = NumberFormat.getInstance();
            nf.setGroupingUsed(true);

            return "Pages: "
                    + pagesX
                    + " x "
                    + pagesY
                    + " ("
                    + nf.format(pagesX * (long) pagesY)
                    + "), "
                    + "tiles:"
                    + tilesPerPageX
                    + " x "
                    + tilesPerPageY
                    + " ("
                    + nf.format(tilesPerPageX * (long) tilesPerPageY)
                    + ")";
        }
    }

    /** @param gridSubsetCoverages grid subset coverage per level, as per {@link GridSubset#getCoverages()} */
    public PagePyramid(final long[][] gridSubsetCoverages, int zoomStart, int zoomStop) {
        this.gridSubsetCoverages = new TreeMap<>();
        for (long[] coverage : gridSubsetCoverages) {
            this.gridSubsetCoverages.put(Integer.valueOf((int) coverage[4]), coverage);
        }
        this.zoomStart = zoomStart;
        this.zoomStop = zoomStop;
        this.pageInfo = new TreeMap<>();
    }

    public int getZoomStart() {
        return zoomStart;
    }

    public int getZoomStop() {
        return zoomStop;
    }

    public PageLevelInfo getPageInfo(final int zoomLevel) {
        Assert.isTrue(
                zoomLevel >= zoomStart,
                "Zoom level must be greater or equal than " + zoomStart + " but was " + zoomLevel + " instead");
        Assert.isTrue(
                zoomLevel <= zoomStop,
                "Zoom level must be lower or equal than " + zoomStop + " but was " + zoomLevel + " instead");

        final Integer key = Integer.valueOf(zoomLevel);
        PageLevelInfo levelInfo = pageInfo.get(key);
        if (levelInfo == null) {
            long[] coverage = this.gridSubsetCoverages.get(key);
            levelInfo = calculatePageInfo(coverage);
            pageInfo.put(key, levelInfo);
        }

        return levelInfo;
    }

    /**
     * @param coverage {@code [minx, miny, maxx, maxy, zoomlevel]} gridsubset coverage for a given zoom level
     * @return {@code [numTilesPerPageX, numTilesPerPageY, numPagesX, numPagesY]} number of pages in both directions for
     *     the given coverage
     */
    public PageLevelInfo calculatePageInfo(final long[] coverage) {

        final long coverageMinX = coverage[0];
        final long coverageMaxX = coverage[2];
        final long coverageMinY = coverage[1];
        final long coverageMaxY = coverage[3];

        final long coverageTilesWide = 1 + coverageMaxX - coverageMinX;
        final long coverageTilesHigh = 1 + coverageMaxY - coverageMinY;

        final int tilesPerPageX = calculateNumTilesPerPage(coverageTilesWide);
        final int tilesPerPageY = calculateNumTilesPerPage(coverageTilesHigh);
        final int numPagesX = (int) Math.ceil((double) coverageTilesWide / tilesPerPageX);
        final int numPagesY = (int) Math.ceil((double) coverageTilesHigh / tilesPerPageY);

        PageLevelInfo pli = new PageLevelInfo(
                numPagesX,
                numPagesY,
                tilesPerPageX,
                tilesPerPageY,
                coverageMinX,
                coverageMinY,
                coverageMaxX,
                coverageMaxY);

        // if (log.isLoggable(Level.FINE)) {
        // log.fine("Coverage: " + Arrays.toString(coverage) + " (" + coverageTilesWide + "x"
        // + coverageTilesHigh + ") tiles. Tiles perpage: " + tilesPerPageX + " x "
        // + tilesPerPageY + " for a total of " + numPagesX + " x " + numPagesY
        // + " pages and " + (tilesPerPageX * (long) tilesPerPageY) + " tiles per page");
        // }
        return pli;
    }

    /**
     * Calculates the number of tiles per page for a gridset coverage over one of its axes (this method doesn't care
     * which of them, either X or Y), client code knows which zoom level it belongs too.
     *
     * <p>The number of pages for each zoom level is different, and currently calculated on a logarithmic basis.
     *
     * @param numTilesInAxis number of tiles in either the x or y axis for the {@link GridSubset} coverage at one of its
     *     zoom levels
     * @return the number of pages corresponding to {@code numTilesInAxis}
     */
    private int calculateNumTilesPerPage(long numTilesInAxis) {
        /*
         * Found log base 1.3 gives a pretty decent progression of number of pages for zoom level
         */
        final double logBase = 1.1;
        // Calculate log base <logBase>
        final double log = (Math.log(numTilesInAxis) / Math.log(logBase));

        // log(1) == 0, so be careful
        final int numTilesPerPage = numTilesInAxis == 1 ? 1 : (int) Math.ceil((numTilesInAxis / log));
        return numTilesPerPage;
    }

    public int[] pageIndexForTile(long x, long y, int level, int[] pageIndexTarget) {
        Assert.notNull(pageIndexTarget, "PageIndexTarget array must be non null");
        Assert.isTrue(pageIndexTarget.length >= 3, "PageIndexTarget array size must be at least 3");

        PageLevelInfo levelInfo = getPageInfo(level);
        final int tilePageX = (int) ((x - levelInfo.coverageMinX) / levelInfo.tilesPerPageX);
        final int tilePageY = (int) ((y - levelInfo.coverageMinY) / levelInfo.tilesPerPageY);

        pageIndexTarget[0] = tilePageX;
        pageIndexTarget[1] = tilePageY;
        pageIndexTarget[2] = level;

        return pageIndexTarget;
    }

    /**
     * Returns a grid subset coverage range suitable for {@link TileRange}
     *
     * @return {@code [minTileX, minTileY, maxTileX, maxTileY, zoomlevel]}
     */
    public long[][] toGridCoverage(int pageX, int pageY, int level) {

        final PageLevelInfo pageLevelInfo = getPageInfo(level);

        final long coverageMinX = pageLevelInfo.coverageMinX;
        final long coverageMinY = pageLevelInfo.coverageMinY;
        final int numTilesPerPageX = pageLevelInfo.tilesPerPageX;
        final int numTilesPerPageY = pageLevelInfo.tilesPerPageY;

        long minTileX = coverageMinX + (long) pageX * numTilesPerPageX;
        long minTileY = coverageMinY + (long) pageY * numTilesPerPageY;
        long maxTileX = minTileX + numTilesPerPageX - 1; // these are indexes, so rest one
        long maxTileY = minTileY + numTilesPerPageY - 1; // same thing

        long[] pageCoverage = {minTileX, minTileY, maxTileX, maxTileY, level};

        final int numLevels = gridSubsetCoverages.size();
        long[][] allLevelsCoverage = new long[numLevels][];
        allLevelsCoverage[level] = pageCoverage;
        return allLevelsCoverage;
    }

    public int getTilesPerPageX(int level) {
        return getPageInfo(level).tilesPerPageX;
    }

    public int getTilesPerPageY(int level) {
        return getPageInfo(level).tilesPerPageY;
    }

    public int getPagesPerLevelX(int level) {
        return getPageInfo(level).pagesX;
    }

    public int getPagesPerLevelY(int level) {
        return getPageInfo(level).pagesY;
    }
}
