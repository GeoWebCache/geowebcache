package org.geowebcache.diskquota;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.grid.GridSubset;

public class TilePageCalculator {

    private static final Log log = LogFactory.getLog(TilePageCalculator.class);

    public long[] pageFor(long[] tileXYZ, GridSubset gridSubset) {
        final long x = tileXYZ[0];
        final long y = tileXYZ[1];
        final long z = tileXYZ[2];

        final long[] coverage = gridSubset.getCoverage((int) z);
        final long tilesWide = coverage[2] - coverage[0];
        final long tilesHigh = coverage[3] - coverage[1];
        double log10W = Math.log10(tilesWide);
        double log10H = Math.log10(tilesHigh);

        // GridSet g;
        // g.getGrids()[0].getExtent();
        return null;
    }

    /**
     * Calculates the page size for each grid subset level (in number of tiles on axes x and y)
     * 
     * @param gs
     * @return
     */
    public int[][] getPageSizes(final GridSubset gs) {

        final long[][] coverages = gs.getCoverages();
        final int numLevels = coverages.length;

        int[][] pageSizes = new int[numLevels][];

        log.info("Calculating page sizes for grid subset " + gs.getName());
        for (int level = 0; level < numLevels; level++) {
            pageSizes[level] = calculatePageSize(coverages[level]);
        }
        return pageSizes;
    }

    private int[] calculatePageSize(final long[] coverage) {

        final long coverageTilesWide = 1 + coverage[2] - coverage[0];
        final long coverageTilesHigh = 1 + coverage[3] - coverage[1];

        final int pageSizeX = calculateNumPages(coverageTilesWide);
        final int pageSizeY = calculateNumPages(coverageTilesHigh);

        int[] pageSize = { pageSizeX, pageSizeY };
        log.info("Coverage: " + Arrays.toString(coverage) + " (" + coverageTilesWide + "x"
                + coverageTilesHigh + "). Page size: " + Arrays.toString(pageSize)
                + " for a total of " + (int) Math.ceil((double) coverageTilesWide / pageSizeX)
                + " x " + (int) Math.ceil((double) coverageTilesHigh / pageSizeY) + " pages and "
                + (pageSizeX * (long) pageSizeY) + " tiles per page");
        return pageSize;
    }

    /**
     * Calculates the number of pages for a gridset coverage over a one of its axes (this method
     * doesn't care which of them, either X or Y), client code knows which zoom level it belongs
     * too.
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
    private int calculateNumPages(long numTilesInAxis) {
        /*
         * Found log base 1.3 gives a pretty decent progression of number of pages for zoom level
         */
        final double logBase = 1.3;
        // Calculate log base <logBase>
        final double log2W = (Math.log(numTilesInAxis) / Math.log(logBase));

        // log(1) == 0, so be careful
        final int pageSize = numTilesInAxis == 1 ? 1 : (int) Math.ceil((numTilesInAxis / log2W));
        return pageSize;
    }
}
