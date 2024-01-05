/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota.storage;

import static org.junit.Assert.assertArrayEquals;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.diskquota.storage.PagePyramid.PageLevelInfo;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.storage.blobstore.file.FilePathUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PagePyramidTest {

    static final Logger LOG = Logging.getLogger(PagePyramidTest.class.getName());

    GridSet world_EPSG3857 =
            new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, false)))
                    .getWorldEpsg3857();

    GridSet world_EPSG4326 =
            new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, false)))
                    .getWorldEpsg4326();

    private long[][] coverages;

    private PagePyramid pyramid;

    @Before
    public void setUp() {
        coverages =
                new long[][] { //
                    {0, 0, 1, 1, 0}, // 2x2 tiles
                    {3, 3, 10, 10, 1}, // 11x11 tiles
                    {0, 0, 101, 101, 2}, // 102x102 tiles
                    {1000, 1000, 3000, 3000, 3} // 2001x2001 tiles
                };
        pyramid = new PagePyramid(coverages, 0, 3);
    }

    @Test
    public void testCalculatePageInfo() {
        GridSubset gridSubSet = GridSubsetFactory.createGridSubSet(world_EPSG3857);
        long[][] gridSubsetCoverages = gridSubSet.getCoverages();
        int zoomStart = gridSubSet.getZoomStart();
        int zoomStop = gridSubSet.getZoomStop();
        PagePyramid pp = new PagePyramid(gridSubsetCoverages, zoomStart, zoomStop);

        printPyramid(zoomStart, zoomStop, pp);
    }

    private void printPyramid(int zoomStart, int zoomStop, PagePyramid pp) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setGroupingUsed(true);

        long totalPages = 0;
        BigInteger totalTiles = BigInteger.ZERO;
        for (int z = zoomStart; z <= zoomStop; z++) {

            PageLevelInfo pageInfo = pp.getPageInfo(z);

            long levelPages = pageInfo.pagesX * pageInfo.pagesY;
            BigInteger tilesPerPage = pageInfo.tilesPerPage;

            totalPages += levelPages;
            totalTiles = totalTiles.add(tilesPerPage.multiply(BigInteger.valueOf(levelPages)));

            LOG.info(
                    FilePathUtils.zeroPadder(z, 2)
                            + ": (total pages ="
                            + nf.format(totalPages)
                            + ") "
                            + pageInfo.toString()
                            + "(level tiles = "
                            + nf.format(tilesPerPage.multiply(BigInteger.valueOf(levelPages)))
                            + ") ");
        }
        LOG.info("Total pages: " + totalPages);
    }

    @Test
    public void testGetPagesPerLevel() {
        Assert.assertEquals(2, pyramid.getPagesPerLevelX(0));
        Assert.assertEquals(2, pyramid.getPagesPerLevelY(0));

        Assert.assertEquals(8, pyramid.getPagesPerLevelX(1));
        Assert.assertEquals(8, pyramid.getPagesPerLevelY(1));

        Assert.assertEquals(34, pyramid.getPagesPerLevelX(2));
        Assert.assertEquals(34, pyramid.getPagesPerLevelY(2));

        Assert.assertEquals(77, pyramid.getPagesPerLevelX(3));
        Assert.assertEquals(77, pyramid.getPagesPerLevelY(3));
    }

    @Test
    public void testGetTilesPerPage() {
        Assert.assertEquals(1, pyramid.getTilesPerPageX(0));
        Assert.assertEquals(1, pyramid.getTilesPerPageY(0));

        Assert.assertEquals(1, pyramid.getTilesPerPageX(1));
        Assert.assertEquals(1, pyramid.getTilesPerPageY(1));

        Assert.assertEquals(3, pyramid.getTilesPerPageX(2));
        Assert.assertEquals(3, pyramid.getTilesPerPageY(2));

        Assert.assertEquals(26, pyramid.getTilesPerPageX(3));
        Assert.assertEquals(26, pyramid.getTilesPerPageY(3));
    }

    @Test
    public void testToGridCoverage() {

        long[][] gridCoverage = pyramid.toGridCoverage(0, 0, 0);
        Assert.assertEquals(asList(0, 0, 0, 0, 0), asList(gridCoverage[0]));

        int level = 1;

        int pageX = 2;
        int pageY = 2;
        int pageZ = 1;
        gridCoverage = pyramid.toGridCoverage(pageX, pageY, pageZ);
        int tilesPerPageX = pyramid.getTilesPerPageX(level);
        int tilesPerPageY = pyramid.getTilesPerPageY(level);
        long[] expected = {
            coverages[level][0] + tilesPerPageX * pageX, //
            coverages[level][1] + tilesPerPageY * pageY, //
            coverages[level][0] + tilesPerPageX * pageX + tilesPerPageX - 1, //
            coverages[level][0] + tilesPerPageY * pageY + tilesPerPageY - 1, //
            pageZ
        };
        Assert.assertEquals(asList(expected), asList(gridCoverage[1]));
    }

    @Test
    public void testPageIndexForTile() throws Exception {
        try {
            pyramid.pageIndexForTile(0, 0, 0, null);
            Assert.fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
        try {
            pyramid.pageIndexForTile(0, 0, 0, new int[2]);
            Assert.fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }

        printPyramid(pyramid.getZoomStart(), pyramid.getZoomStop(), pyramid);

        // grid coverages:
        // { 0, 0, 1, 1, 0 },// 1x1 tiles
        int[] pageIndexTarget = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        int[] expected = {0, 0, 0};
        pyramid.pageIndexForTile(0, 0, 0, pageIndexTarget);
        assertArrayEquals(Arrays.toString(pageIndexTarget), expected, pageIndexTarget);

        expected = new int[] {1, 1, 0};
        pyramid.pageIndexForTile(1, 1, 0, pageIndexTarget);
        assertArrayEquals(Arrays.toString(pageIndexTarget), expected, pageIndexTarget);

        // grid coverages:
        // { 3, 3, 10, 10, 1 },// 1x1 tiles
        expected = new int[] {0, 0, 1};
        pyramid.pageIndexForTile(3, 3, 1, pageIndexTarget);
        assertArrayEquals(Arrays.toString(pageIndexTarget), expected, pageIndexTarget);

        expected = new int[] {1, 1, 1};
        pyramid.pageIndexForTile(4, 4, 1, pageIndexTarget);
        assertArrayEquals(Arrays.toString(pageIndexTarget), expected, pageIndexTarget);

        expected = new int[] {7, 7, 1};
        pyramid.pageIndexForTile(10, 10, 1, pageIndexTarget);
        assertArrayEquals(Arrays.toString(pageIndexTarget), expected, pageIndexTarget);

        // grid coverages:
        // { 1000, 1000, 3000, 3000, 3 } // 77x77 pages, 26x26 tiles
        expected = new int[] {0, 0, 3};
        pyramid.pageIndexForTile(1000, 1000, 3, pageIndexTarget);
        assertArrayEquals(Arrays.toString(pageIndexTarget), expected, pageIndexTarget);

        expected = new int[] {1, 1, 3};
        pyramid.pageIndexForTile(1026, 1026, 3, pageIndexTarget);
        assertArrayEquals(Arrays.toString(pageIndexTarget), expected, pageIndexTarget);
    }

    private List<Long> asList(long... coverage) {
        List<Long> list = new ArrayList<>();
        for (long l : coverage) {
            list.add(Long.valueOf(l));
        }
        return list;
    }
}
