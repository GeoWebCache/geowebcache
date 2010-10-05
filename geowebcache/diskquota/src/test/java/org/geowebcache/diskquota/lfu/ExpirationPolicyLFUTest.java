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
package org.geowebcache.diskquota.lfu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import org.geowebcache.diskquota.paging.TilePage;

/**
 * @author groldan
 * 
 */
public class ExpirationPolicyLFUTest extends TestCase {

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.lfu.ExpirationPolicyLFU#sortPagesForExpiration(java.util.List)}
     * .
     */
    public void testSortPagesForExpiration() {
        List<TilePage> pages = new ArrayList<TilePage>();
        long numHits = 1000;
        TilePage mostUsed = new TilePage("testLayer", "EPSG:4326", 1, 1, 1, numHits, 10, 0);

        numHits = 10;
        TilePage leastUsed = new TilePage("testLayer", "EPSG:4326", 0, 0, 0, numHits, 100, 0);

        numHits = 100;
        TilePage moreOrLessUsed = new TilePage("testLayer", "EPSG:4326", 0, 0, 1, numHits, 100, 0);

        pages.add(mostUsed);
        pages.add(leastUsed);
        pages.add(moreOrLessUsed);

        Collections.sort(pages, ExpirationPolicyLFU.LFUSorter);
        assertNotNull(pages);
        assertEquals(3, pages.size());
        assertSame(pages, pages);
        assertSame(leastUsed, pages.get(0));
        assertSame(moreOrLessUsed, pages.get(1));
        assertSame(mostUsed, pages.get(2));
    }

    /**
     * In the event two pages have the same number of hits, the one at the higher zoom level takes
     * precedence
     */
    public void testSortPagesForExpirationSameAccessTime() {
        List<TilePage> pages = new ArrayList<TilePage>();
        TilePage lowerZoomLevel = new TilePage("testLayer", "EPSG:4326", 1, 1, 1, 1000, 1, 0);
        TilePage higherZoomLevel = new TilePage("testLayer", "EPSG:4326", 1, 1, 2, 1000, 1, 0);

        pages.add(lowerZoomLevel);
        pages.add(higherZoomLevel);

        Collections.sort(pages, ExpirationPolicyLFU.LFUSorter);
        assertNotNull(pages);
        assertEquals(2, pages.size());
        assertSame(pages, pages);
        assertSame(higherZoomLevel, pages.get(0));
        assertSame(lowerZoomLevel, pages.get(1));
    }
}
