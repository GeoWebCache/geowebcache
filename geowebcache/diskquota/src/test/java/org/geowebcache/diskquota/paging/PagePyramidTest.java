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

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

public class PagePyramidTest extends TestCase {

    private static final String GRIDSET = "gridset";

    private static final String LAYER = "layer";

    private long[][] coverages;

    private PagePyramid pyramid;

    public void setUp() {
        coverages = new long[][] {//
        { 0, 0, 1, 1, 0 },// 2x2 tiles
                { 3, 3, 10, 10, 1 },// 11x11 tiles
                { 0, 0, 101, 101, 2 },// 102x102 tiles
                { 1000, 1000, 3000, 3000, 3 } // 2001x2001 tiles
        };
        pyramid = new PagePyramid(LAYER, GRIDSET, coverages);

    }

    public void testGetPagesPerLevel() {
        assertEquals(2, pyramid.getPagesPerLevelX(0));
        assertEquals(2, pyramid.getPagesPerLevelY(0));

        assertEquals(4, pyramid.getPagesPerLevelX(1));
        assertEquals(4, pyramid.getPagesPerLevelY(1));

        assertEquals(17, pyramid.getPagesPerLevelX(2));
        assertEquals(17, pyramid.getPagesPerLevelY(2));

        assertEquals(29, pyramid.getPagesPerLevelX(3));
        assertEquals(29, pyramid.getPagesPerLevelY(3));
    }

    public void testGetTilesPerPage() {
        assertEquals(1, pyramid.getTilesPerPageX(0));
        assertEquals(1, pyramid.getTilesPerPageY(0));

        assertEquals(2, pyramid.getTilesPerPageX(1));
        assertEquals(2, pyramid.getTilesPerPageY(1));

        assertEquals(6, pyramid.getTilesPerPageX(2));
        assertEquals(6, pyramid.getTilesPerPageY(2));

        assertEquals(70, pyramid.getTilesPerPageX(3));
        assertEquals(70, pyramid.getTilesPerPageY(3));
    }

    public void testGetPageFor() {
        TilePage page;
        assertFalse(pyramid.pages.containsKey(new int[] { 0, 0, 0 }));
        page = pyramid.pageFor(0, 0, 0);
        assertEquals(new TilePage(LAYER, GRIDSET, 0, 0, 0), page);
        assertTrue(pyramid.pages.containsKey(new int[] { 0, 0, 0 }));

        // tiles per page is 2x2, tile 3,3,1 corresponds to page 0,0,1 cause gridset coverage starts
        // at tile 3,3
        assertFalse(pyramid.pages.containsKey(new int[] { 0, 0, 1 }));
        page = pyramid.pageFor(3, 3, 1);
        assertEquals(new TilePage(LAYER, GRIDSET, 0, 0, 1), page);
        assertTrue(pyramid.pages.containsKey(new int[] { 0, 0, 1 }));
    }

    public void testToGridCoverage() {
        TilePage page;
        long[][] gridCoverage;

        page = pyramid.pageFor(0, 0, 0);
        gridCoverage = pyramid.toGridCoverage(page);
        assertEquals(asList(0, 0, 0, 0, 0), asList(gridCoverage[0]));

        int level = 1;
        page = pyramid.pageFor(2, 2, level);

        gridCoverage = pyramid.toGridCoverage(page);
        int tilesPerPageX = pyramid.getTilesPerPageX(level);
        int tilesPerPageY = pyramid.getTilesPerPageY(level);
        long[] expected = { coverages[level][0] + tilesPerPageX * page.getX(),//
                coverages[level][1] + tilesPerPageY * page.getY(), //
                coverages[level][0] + tilesPerPageX * page.getX() + tilesPerPageX - 1,//
                coverages[level][0] + tilesPerPageY * page.getY() + tilesPerPageY - 1,//
                page.getZ() };
        assertEquals(asList(expected), asList(gridCoverage[1]));
    }

    private List<Long> asList(long... coverage) {
        List<Long> list = new ArrayList<Long>();
        for (long l : coverage) {
            list.add(Long.valueOf(l));
        }
        return list;
    }

}
