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
package org.geowebcache.diskquota.storage;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import junit.framework.TestCase;

import org.geowebcache.diskquota.storage.PagePyramid.PageLevelInfo;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.storage.blobstore.file.FilePathUtils;

public class PagePyramidTest extends TestCase {

    GridSet world_EPSG3857 = new GridSetBroker(true, false).WORLD_EPSG3857;

    GridSet world_EPSG4326 = new GridSetBroker(true, false).WORLD_EPSG4326;

    private long[][] coverages;

    private PagePyramid pyramid;

    public void setUp() {
        coverages = new long[][] {//
        { 0, 0, 1, 1, 0 },// 2x2 tiles
                { 3, 3, 10, 10, 1 },// 11x11 tiles
                { 0, 0, 101, 101, 2 },// 102x102 tiles
                { 1000, 1000, 3000, 3000, 3 } // 2001x2001 tiles
        };
        pyramid = new PagePyramid(coverages, 0, 3);

    }

    public void testCalculatePageInfo() {
        GridSubset gridSubSet = GridSubsetFactory.createGridSubSet(world_EPSG3857);
        long[][] gridSubsetCoverages = gridSubSet.getCoverages();
        int zoomStart = gridSubSet.getZoomStart();
        int zoomStop = gridSubSet.getZoomStop();
        PagePyramid pp = new PagePyramid(gridSubsetCoverages, zoomStart, zoomStop);

        printPyramid(zoomStart, zoomStop, pp);
    }

    private void printPyramid(int zoomStart, int zoomStop, PagePyramid pp) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("es"));
        nf.setGroupingUsed(true);

        long totalPages = 0;
        BigInteger totalTiles = BigInteger.ZERO;
        for (int z = zoomStart; z <= zoomStop; z++) {

            PageLevelInfo pageInfo = pp.getPageInfo(z);

            long levelPages = pageInfo.pagesX * pageInfo.pagesY;
            BigInteger tilesPerPage = pageInfo.tilesPerPage;

            totalPages += levelPages;
            totalTiles = totalTiles.add(tilesPerPage.multiply(BigInteger.valueOf(levelPages)));

            System.out.println(FilePathUtils.zeroPadder(z, 2) + ": (total pages ="
                    + nf.format(totalPages) + ") " + pageInfo.toString() + "(level tiles = "
                    + nf.format(tilesPerPage.multiply(BigInteger.valueOf(levelPages))) + ") ");
        }
        System.out.println("Total pages: " + totalPages);
    }

    public void testGetPagesPerLevel() {
        assertEquals(2, pyramid.getPagesPerLevelX(0));
        assertEquals(2, pyramid.getPagesPerLevelY(0));

        assertEquals(8, pyramid.getPagesPerLevelX(1));
        assertEquals(8, pyramid.getPagesPerLevelY(1));

        assertEquals(34, pyramid.getPagesPerLevelX(2));
        assertEquals(34, pyramid.getPagesPerLevelY(2));

        assertEquals(77, pyramid.getPagesPerLevelX(3));
        assertEquals(77, pyramid.getPagesPerLevelY(3));
    }

    public void testGetTilesPerPage() {
        assertEquals(1, pyramid.getTilesPerPageX(0));
        assertEquals(1, pyramid.getTilesPerPageY(0));

        assertEquals(1, pyramid.getTilesPerPageX(1));
        assertEquals(1, pyramid.getTilesPerPageY(1));

        assertEquals(3, pyramid.getTilesPerPageX(2));
        assertEquals(3, pyramid.getTilesPerPageY(2));

        assertEquals(26, pyramid.getTilesPerPageX(3));
        assertEquals(26, pyramid.getTilesPerPageY(3));
    }

    public void testToGridCoverage() {

        long[][] gridCoverage;

        gridCoverage = pyramid.toGridCoverage(0, 0, 0);
        assertEquals(asList(0, 0, 0, 0, 0), asList(gridCoverage[0]));

        int level = 1;

        int pageX = 2;
        int pageY = 2;
        int pageZ = 1;
        gridCoverage = pyramid.toGridCoverage(pageX, pageY, pageZ);
        int tilesPerPageX = pyramid.getTilesPerPageX(level);
        int tilesPerPageY = pyramid.getTilesPerPageY(level);
        long[] expected = { coverages[level][0] + tilesPerPageX * pageX,//
                coverages[level][1] + tilesPerPageY * pageY, //
                coverages[level][0] + tilesPerPageX * pageX + tilesPerPageX - 1,//
                coverages[level][0] + tilesPerPageY * pageY + tilesPerPageY - 1,//
                pageZ };
        assertEquals(asList(expected), asList(gridCoverage[1]));
    }

    public void testPageIndexForTile() throws Exception {
        try {
            pyramid.pageIndexForTile(0, 0, 0, null);
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            pyramid.pageIndexForTile(0, 0, 0, new int[2]);
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        printPyramid(pyramid.getZoomStart(), pyramid.getZoomStop(), pyramid);

        // grid coverages:
        // { 0, 0, 1, 1, 0 },// 1x1 tiles
        int[] pageIndexTarget = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        int[] expected = { 0, 0, 0 };
        pyramid.pageIndexForTile(0, 0, 0, pageIndexTarget);
        assertTrue(Arrays.toString(pageIndexTarget), Arrays.equals(expected, pageIndexTarget));

        expected = new int[] { 1, 1, 0 };
        pyramid.pageIndexForTile(1, 1, 0, pageIndexTarget);
        assertTrue(Arrays.toString(pageIndexTarget), Arrays.equals(expected, pageIndexTarget));

        // grid coverages:
        // { 3, 3, 10, 10, 1 },// 1x1 tiles
        expected = new int[] { 0, 0, 1 };
        pyramid.pageIndexForTile(3, 3, 1, pageIndexTarget);
        assertTrue(Arrays.toString(pageIndexTarget), Arrays.equals(expected, pageIndexTarget));

        expected = new int[] { 1, 1, 1 };
        pyramid.pageIndexForTile(4, 4, 1, pageIndexTarget);
        assertTrue(Arrays.toString(pageIndexTarget), Arrays.equals(expected, pageIndexTarget));

        expected = new int[] { 7, 7, 1 };
        pyramid.pageIndexForTile(10, 10, 1, pageIndexTarget);
        assertTrue(Arrays.toString(pageIndexTarget), Arrays.equals(expected, pageIndexTarget));

        // grid coverages:
        // { 1000, 1000, 3000, 3000, 3 } // 77x77 pages, 26x26 tiles
        expected = new int[] { 0, 0, 3 };
        pyramid.pageIndexForTile(1000, 1000, 3, pageIndexTarget);
        assertTrue(Arrays.toString(pageIndexTarget), Arrays.equals(expected, pageIndexTarget));

        expected = new int[] { 1, 1, 3 };
        pyramid.pageIndexForTile(1026, 1026, 3, pageIndexTarget);
        assertTrue(Arrays.toString(pageIndexTarget), Arrays.equals(expected, pageIndexTarget));
    }

    private List<Long> asList(long... coverage) {
        List<Long> list = new ArrayList<Long>();
        for (long l : coverage) {
            list.add(Long.valueOf(l));
        }
        return list;
    }

}
