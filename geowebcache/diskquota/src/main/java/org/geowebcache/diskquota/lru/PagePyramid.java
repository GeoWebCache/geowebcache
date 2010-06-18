package org.geowebcache.diskquota.lru;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
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
    private final int[][] pageInfo;

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
    private final TreeMap<int[], TilePage> pages = new TreeMap<int[], TilePage>(
            TILEPAGE_KEY_COMPARATOR);

    private final GridSubset gridSubset;

    /**
     * 
     * @param gs
     */
    PagePyramid(final GridSubset gs) {
        this.gridSubset = gs;
        this.pageInfo = calculatePageSizes(gs);
    }

    /**
     * Calculates the page size for each grid subset level (in number of tiles on axes x and y)
     * 
     * @param gs
     * @return
     */
    private int[][] calculatePageSizes(final GridSubset gs) {

        final long[][] coverages = gs.getCoverages();
        final int numLevels = coverages.length;

        int[][] pageSizes = new int[numLevels][];

        log.info("Calculating page sizes for grid subset " + gs.getName());
        for (int level = 0; level < numLevels; level++) {
            pageSizes[level] = calculatePageInfo(coverages[level]);
        }
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
    private int[] calculatePageInfo(final long[] coverage) {

        final long coverageTilesWide = 1 + coverage[2] - coverage[0];
        final long coverageTilesHigh = 1 + coverage[3] - coverage[1];

        final int tilesPerPageX = calculateNumTilesPerPage(coverageTilesWide);
        final int tilesPerPageY = calculateNumTilesPerPage(coverageTilesHigh);
        final int numPagesX = (int) Math.ceil((double) coverageTilesWide / tilesPerPageX);
        final int numPagesY = (int) Math.ceil((double) coverageTilesHigh / tilesPerPageY);

        int[] pageInfo = { tilesPerPageX, tilesPerPageY, numPagesX, numPagesY };

        log.info("Coverage: " + Arrays.toString(coverage) + " (" + coverageTilesWide + "x"
                + coverageTilesHigh + ") tiles. Tiles perpage: " + tilesPerPageX + " x "
                + tilesPerPageY + " for a total of " + numPagesX + " x " + numPagesY
                + " pages and " + (tilesPerPageX * (long) tilesPerPageY) + " tiles per page");
        return pageInfo;
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

    public TilePage pageFor(long[] tileXYZ) {
        final int level = (int) tileXYZ[2];
        final int tilesPerPageX = pageInfo[level][0];
        final int tilesPerPageY = pageInfo[level][1];

        int[] pageIndex = { (int) (tileXYZ[0] / tilesPerPageX), // pageX
                (int) (tileXYZ[1] / tilesPerPageY), // pageY
                level };

        TilePage tilePage = pages.get(pageIndex);
        if (tilePage == null) {
            synchronized (pages) {
                tilePage = pages.get(pageIndex);
                if (tilePage == null) {
                    tilePage = new TilePage(pageIndex[0], pageIndex[1], pageIndex[2]);
                    pages.put(pageIndex, tilePage);
                }
            }
        }

        return tilePage;
    }

    /**
     * Builds and returns a Map of all pages, including the ones that are not yet contained due to
     * no usage.
     * <p>
     * Clones are returned for existing pages
     * </p>
     * 
     * @return
     */
    public Map<int[], TilePage> getAllPages() {
        // initialize the returning map with the available TilePages
        TreeMap<int[], TilePage> allPages = new TreeMap<int[], TilePage>(TILEPAGE_KEY_COMPARATOR);
        allPages.putAll(this.pages);

        // fill in all unavailable tile pages
        int[] pageIndex = new int[3];
        for (int level = 0; level < pageInfo.length; level++) {
            int[] page = pageInfo[level];
            final int numPagesX = page[2];
            final int numPagesY = page[3];

            pageIndex[2] = level;
            for (int pageY = 0; pageY < numPagesY; pageY++) {
                pageIndex[1] = pageY;
                for (int pageX = 0; pageX < numPagesX; pageX++) {
                    pageIndex[0] = pageX;
                    TilePage tilePage = allPages.get(pageIndex);
                    if (tilePage == null) {
                        allPages.put(pageIndex.clone(), new TilePage(pageX, pageY, level));
                    } else {
                        allPages.put(pageIndex.clone(), new TilePage(pageX, pageY, level, tilePage
                                .getNumHits()));
                    }
                }
            }

        }
        return allPages;
    }

    /**
     * Returns a grid subset coverage range suitable for {@link TileRange}
     * 
     * @param page
     * @return {@code [minTileX, minTileY, maxTileX, maxTileY, zoomlevel]}
     */
    public long[][] toGridCoverage(TilePage page) {

        final int level = page.getZ();
        final int[] pageLevelInfo = this.pageInfo[level];

        // {minx,miny,maxx,maxy,z}
        long[] gridSubsetLevelCoverage = this.gridSubset.getCoverage(level);

        final int numTilesPerPageX = pageLevelInfo[0];
        final int numTilesPerPageY = pageLevelInfo[1];

        final int pageX = page.getX();
        final int pageY = page.getY();

        long minTileX = gridSubsetLevelCoverage[0] + pageX * numTilesPerPageX;
        long minTileY = gridSubsetLevelCoverage[1] + pageY * numTilesPerPageY;
        long maxTileX = minTileX + numTilesPerPageX - 1;// these are indexes, so rest one
        long maxTileY = minTileY + numTilesPerPageY - 1;// same thing

        long[] pageCoverage = { minTileX, minTileY, maxTileX, maxTileY, level };

        int numLevels = pageInfo.length;
        long[][] allLevelsCoverage = new long[numLevels][];
        allLevelsCoverage[level] = pageCoverage;
        return allLevelsCoverage;
    }
}