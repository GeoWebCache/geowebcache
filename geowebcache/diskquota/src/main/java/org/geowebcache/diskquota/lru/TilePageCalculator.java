package org.geowebcache.diskquota.lru;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.grid.GridSubset;

public class TilePageCalculator {

    private static final Log log = LogFactory.getLog(TilePageCalculator.class);

    /**
     * Pyramid of tile pages for a given {@link GridSubset}
     * 
     * @author groldan
     */
    private static class PageRange {

        private final long[][] gridSubsetCoverages;

        private final int[][] pageSizes;

        /**
         * Key is a page index {x, y, z}, value is the TilePage itself
         */
        private final TreeMap<int[], TilePage> pages = new TreeMap<int[], TilePage>(
                new Comparator<int[]>() {
                    public int compare(int[] p1, int[] p2) {
                        int d = 0;
                        for (int axis = 2; axis >= 0 && d == 0; axis--) {
                            d = p2[axis] - p1[axis];
                        }
                        return d;
                    }
                });

        public PageRange(final GridSubset gs) {
            this.gridSubsetCoverages = gs.getCoverages();
            this.pageSizes = calculatePageSizes(gs);
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
                pageSizes[level] = calculatePageSize(coverages[level]);
            }
            return pageSizes;
        }

        /**
         * 
         * @param coverage
         *            {@code [minx, miny, maxx, maxy, zoomlevel]} gridsubset coverage for a given
         *            zoom level
         * @return {@code [pageSizeX, pageSizeY]} number of pages in both directions for the given
         *         coverage
         */
        private int[] calculatePageSize(final long[] coverage) {

            final long coverageTilesWide = 1 + coverage[2] - coverage[0];
            final long coverageTilesHigh = 1 + coverage[3] - coverage[1];

            final int pageSizeX = calculateNumPages(coverageTilesWide);
            final int pageSizeY = calculateNumPages(coverageTilesHigh);

            int[] pageSize = { pageSizeX, pageSizeY };
            log.info("Coverage: " + Arrays.toString(coverage) + " (" + coverageTilesWide + "x"
                    + coverageTilesHigh + "). Page size: " + Arrays.toString(pageSize)
                    + " for a total of " + (int) Math.ceil((double) coverageTilesWide / pageSizeX)
                    + " x " + (int) Math.ceil((double) coverageTilesHigh / pageSizeY)
                    + " pages and " + (pageSizeX * (long) pageSizeY) + " tiles per page");
            return pageSize;
        }

        /**
         * Calculates the number of pages for a gridset coverage over one of its axes (this method
         * doesn't care which of them, either X or Y), client code knows which zoom level it belongs
         * too.
         * <p>
         * The number of pages for each zoom level is different, and currently calculated on a
         * logarithmic basis.
         * </p>
         * 
         * @param numTilesInAxis
         *            number of tiles in either the x or y axis for the {@link GridSubset} coverage
         *            at one of its zoom levels
         * @return the number of pages corresponding to {@code numTilesInAxis}
         */
        private int calculateNumPages(long numTilesInAxis) {
            /*
             * Found log base 1.3 gives a pretty decent progression of number of pages for zoom
             * level
             */
            final double logBase = 1.3;
            // Calculate log base <logBase>
            final double log = (Math.log(numTilesInAxis) / Math.log(logBase));

            // log(1) == 0, so be careful
            final int pageSize = numTilesInAxis == 1 ? 1 : (int) Math.ceil((numTilesInAxis / log));
            return pageSize;
        }

        public TilePage pageFor(long[] tileXYZ) {
            final int level = (int) tileXYZ[2];
            final int tilesPerPageX = pageSizes[level][0];
            final int tilesPerPageY = pageSizes[level][1];

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
    }

    /**
     * Map<gridSubsetId,{level, {pagesWide, pagesHigh}}>
     */
    final Map<String, PageRange> pageRangesPerGridSubset = new HashMap<String, PageRange>();

    public TilePageCalculator(Hashtable<String, GridSubset> gridSubsets) {
        for (GridSubset gs : gridSubsets.values()) {
            pageRangesPerGridSubset.put(gs.getName(), new PageRange(gs));
        }
    }

    public TilePage pageFor(long[] tileXYZ, String gridSetId) {
        PageRange pageRange = pageRangesPerGridSubset.get(gridSetId);
        return pageRange.pageFor(tileXYZ);
    }

    /**
     * Returns the page size for each grid subset level (in number of tiles on axes x and y)
     * 
     * @return
     */
    public int[][] getPageSizes(final String gridSubsetId) {
        int[][] pageSizes = this.pageRangesPerGridSubset.get(gridSubsetId).pageSizes;
        int[][] copy = new int[pageSizes.length][];
        for (int i = 0; i < pageSizes.length; i++) {
            copy[i] = pageSizes[i].clone();
        }
        return copy;
    }
}
