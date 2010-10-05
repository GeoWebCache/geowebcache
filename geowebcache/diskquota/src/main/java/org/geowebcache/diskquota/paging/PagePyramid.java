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
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota.paging;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.storage.TileRange;

/**
 * Pyramid of tile pages for a given {@link GridSubset}
 * <p>
 * This is a support class for {@link TilePageCalculator}, hence package visible.
 * </p>
 * 
 * @author groldan
 */
class PagePyramid {

    private static final Log log = LogFactory.getLog(PagePyramid.class);

    /**
     * {@code [level][numTilesPerPageX, numTilesPerPageY, numPagesX, numPagesY]}
     */
    private final PageLevelInfo[] pageInfo;

    private static final class PageLevelInfo {
        public final int level;

        public final int pagesX;

        public final int pagesY;

        public final int tilesPerPageX;

        public final int tilesPerPageY;

        public final long coverageMinX;

        public final long coverageMinY;

        public final long coverageMaxX;

        public final long coverageMaxY;

        public PageLevelInfo(int level, int pagesX, int pagesY, int tilesPerPageX,
                int tilesPerPageY, long coverageMinX, long coverageMinY, long coverageMaxX,
                long coverageMaxY) {
            this.level = level;
            this.pagesX = pagesX;
            this.pagesY = pagesY;
            this.tilesPerPageX = tilesPerPageX;
            this.tilesPerPageY = tilesPerPageY;
            this.coverageMinX = coverageMinX;
            this.coverageMinY = coverageMinY;
            this.coverageMaxX = coverageMaxX;
            this.coverageMaxY = coverageMaxY;
        }
    }

    /**
     * Comparator used to store TilePages in a {@link TreeMap} using the tile page {@code int[]}
     * array as keys
     */
    private static final Comparator<int[]> TILEPAGE_KEY_COMPARATOR = new Comparator<int[]>() {
        public int compare(int[] p1, int[] p2) {
            int d = 0;
            for (int axis = 2; axis >= 0 && d == 0; axis--) {
                d = p2[axis] - p1[axis];
            }
            return d;
        }
    };

    /**
     * Key is a page index {x, y, z}, value is the TilePage itself
     */
    final TreeMap<int[], TilePage> pages = new TreeMap<int[], TilePage>(TILEPAGE_KEY_COMPARATOR);

    private final String gridSubsetId;

    private final String layerName;

    /**
     * 
     * @param layerName
     *            the name of the layer
     * @param gridsetId
     *            the name of the {@link GridSubset}
     * @param gridSubsetCoverages
     *            grid subset coverage per level, as per {@link GridSubset#getCoverages()}
     */
    public PagePyramid(final String layerName, final String gridsetId,
            final long[][] gridSubsetCoverages) {
        this.layerName = layerName;
        this.gridSubsetId = gridsetId;
        this.pageInfo = calculatePageSizes(gridSubsetCoverages);
    }

    /**
     * Calculates the page size for each grid subset level (in number of tiles on axes x and y)
     * 
     * @param gridSubsetCoverages
     * 
     * @return
     */
    private PageLevelInfo[] calculatePageSizes(final long[][] coverages) {
        final int numLevels = coverages.length;

        PageLevelInfo[] pageSizes = new PageLevelInfo[numLevels];

        BigInteger totalTiles = BigInteger.valueOf(0);
        long totalPages = 0;

        // pageInfo = { tilesPerPageX, tilesPerPageY, numPagesX, numPagesY };

        log.info("Calculating page sizes for grid subset " + gridSubsetId);
        for (int level = 0; level < numLevels; level++) {
            pageSizes[level] = calculatePageInfo(coverages[level]);
            int pages = pageSizes[level].pagesX * pageSizes[level].pagesY;
            totalTiles = totalTiles.add(BigInteger.valueOf((long) pageSizes[level].tilesPerPageX
                    * (long) pageSizes[level].tilesPerPageY * pages));
            totalPages += pages;
        }
        log.info("----- Total tiles: " + totalTiles + ". Total pages: " + totalPages);
        return pageSizes;
    }

    /**
     * 
     * @param coverage
     *            {@code [minx, miny, maxx, maxy, zoomlevel]} gridsubset coverage for a given zoom
     *            level
     * @return {@code [numTilesPerPageX, numTilesPerPageY, numPagesX, numPagesY]} number of pages in
     *         both directions for the given coverage
     */
    private PageLevelInfo calculatePageInfo(final long[] coverage) {

        final int level = (int) coverage[4];
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

        PageLevelInfo pli = new PageLevelInfo(level, numPagesX, numPagesY, tilesPerPageX,
                tilesPerPageY, coverageMinX, coverageMinY, coverageMaxX, coverageMaxY);

        log.info("Coverage: " + Arrays.toString(coverage) + " (" + coverageTilesWide + "x"
                + coverageTilesHigh + ") tiles. Tiles perpage: " + tilesPerPageX + " x "
                + tilesPerPageY + " for a total of " + numPagesX + " x " + numPagesY
                + " pages and " + (tilesPerPageX * (long) tilesPerPageY) + " tiles per page");
        return pli;
    }

    /**
     * Calculates the number of tiles per page for a gridset coverage over one of its axes (this
     * method doesn't care which of them, either X or Y), client code knows which zoom level it
     * belongs too.
     * <p>
     * The number of pages for each zoom level is different, and currently calculated on a
     * logarithmic basis.
     * </p>
     * 
     * @param numTilesInAxis
     *            number of tiles in either the x or y axis for the {@link GridSubset} coverage at
     *            one of its zoom levels
     * @return the number of pages corresponding to {@code numTilesInAxis}
     */
    private int calculateNumTilesPerPage(long numTilesInAxis) {
        /*
         * Found log base 1.3 gives a pretty decent progression of number of pages for zoom level
         */
        final double logBase = 1.3;
        // Calculate log base <logBase>
        final double log = (Math.log(numTilesInAxis) / Math.log(logBase));

        // log(1) == 0, so be careful
        final int numTilesPerPage = numTilesInAxis == 1 ? 1 : (int) Math
                .ceil((numTilesInAxis / log));
        return numTilesPerPage;
    }

    public TilePage pageFor(long x, long y, int level) {
        final PageLevelInfo levelInfo = pageInfo[level];

        final int tilePageX = (int) ((x - levelInfo.coverageMinX) / levelInfo.tilesPerPageX);
        final int tilePageY = (int) ((y - levelInfo.coverageMinY) / levelInfo.tilesPerPageY);
        final int[] pageIndex = { tilePageX, tilePageY, level };

        TilePage tilePage = pages.get(pageIndex);
        if (tilePage == null) {
            synchronized (pages) {
                tilePage = pages.get(pageIndex);
                if (tilePage == null) {
                    tilePage = new TilePage(layerName, gridSubsetId, tilePageX, tilePageY, level);
                    pages.put(pageIndex, tilePage);
                }
            }
        }

        return tilePage;
    }

    /**
     * Returns a grid subset coverage range suitable for {@link TileRange}
     * 
     * @param page
     * @return {@code [minTileX, minTileY, maxTileX, maxTileY, zoomlevel]}
     */
    public long[][] toGridCoverage(TilePage page) {

        final int level = page.getZ();
        final PageLevelInfo pageLevelInfo = this.pageInfo[level];

        final long coverageMinX = pageLevelInfo.coverageMinX;
        final long coverageMinY = pageLevelInfo.coverageMinY;
        final int numTilesPerPageX = pageLevelInfo.tilesPerPageX;
        final int numTilesPerPageY = pageLevelInfo.tilesPerPageY;

        final int pageX = page.getX();
        final int pageY = page.getY();

        long minTileX = coverageMinX + (long) pageX * numTilesPerPageX;
        long minTileY = coverageMinY + (long) pageY * numTilesPerPageY;
        long maxTileX = minTileX + numTilesPerPageX - 1;// these are indexes, so rest one
        long maxTileY = minTileY + numTilesPerPageY - 1;// same thing

        long[] pageCoverage = { minTileX, minTileY, maxTileX, maxTileY, level };

        final int numLevels = pageInfo.length;
        long[][] allLevelsCoverage = new long[numLevels][];
        allLevelsCoverage[level] = pageCoverage;
        return allLevelsCoverage;
    }

    public Collection<TilePage> getPages() {
        return this.pages.values();
    }

    public void setPages(List<TilePage> pages) {
        int[] key;

        for (TilePage page : pages) {
            key = new int[] { page.getX(), page.getY(), page.getZ() };
            this.pages.put(key, page);
        }
    }

    public int getTilesPerPageX(int level) {
        return pageInfo[level].tilesPerPageX;
    }

    public int getTilesPerPageY(int level) {
        return pageInfo[level].tilesPerPageY;
    }

    public int getPagesPerLevelX(int level) {
        return pageInfo[level].pagesX;
    }

    public int getPagesPerLevelY(int level) {
        return pageInfo[level].pagesY;
    }
}