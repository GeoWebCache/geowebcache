/**
 * 
 */
package org.geowebcache.diskquota.lfu;

import java.util.ArrayList;
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
        TilePage mostUsed = new TilePage(1, 1, 1, numHits, 10);

        numHits = 10;
        TilePage leastUsed = new TilePage(0, 0, 0, numHits, 100);

        numHits = 100;
        TilePage moreOrLessUsed = new TilePage(0, 0, 1, numHits, 100);

        pages.add(mostUsed);
        pages.add(leastUsed);
        pages.add(moreOrLessUsed);

        List<TilePage> sortPages = ExpirationPolicyLFU.sortPages(pages);
        assertNotNull(sortPages);
        assertEquals(3, sortPages.size());
        assertSame(pages, sortPages);
        assertSame(leastUsed, sortPages.get(0));
        assertSame(moreOrLessUsed, sortPages.get(1));
        assertSame(mostUsed, sortPages.get(2));
    }

    /**
     * In the event two pages have the same number of hits, the one at the higher zoom level takes
     * precedence
     */
    public void testSortPagesForExpirationSameAccessTime() {
        List<TilePage> pages = new ArrayList<TilePage>();
        TilePage lowerZoomLevel = new TilePage(1, 1, 1, 1000, 1);
        TilePage higherZoomLevel = new TilePage(1, 1, 2, 1000, 1);

        pages.add(lowerZoomLevel);
        pages.add(higherZoomLevel);

        List<TilePage> sortPages = ExpirationPolicyLFU.sortPages(pages);
        assertNotNull(sortPages);
        assertEquals(2, sortPages.size());
        assertSame(pages, sortPages);
        assertSame(higherZoomLevel, sortPages.get(0));
        assertSame(lowerZoomLevel, sortPages.get(1));
    }
}
