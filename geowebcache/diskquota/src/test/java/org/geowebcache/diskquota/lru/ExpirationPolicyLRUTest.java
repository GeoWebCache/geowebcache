package org.geowebcache.diskquota.lru;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.geowebcache.diskquota.paging.TilePage;

public class ExpirationPolicyLRUTest extends TestCase {

    @SuppressWarnings("serial")
    public void testSortPagesForExpiration() {
        List<TilePage> pages = new ArrayList<TilePage>();

        TilePage mostRecentlyUsed = new TilePage(1, 1, 1, 1, 10, 1000);
        TilePage leastRecentlyUsed = new TilePage(0, 0, 0, 1, 100, 100);
        TilePage moreOrLessRecentlyUsed = new TilePage(0, 0, 1, 1, 100, 500);

        mostRecentlyUsed.markHit();

        pages.add(mostRecentlyUsed);
        pages.add(leastRecentlyUsed);
        pages.add(moreOrLessRecentlyUsed);

        List<TilePage> sortPages = ExpirationPolicyLRU.sortPages(pages);
        assertNotNull(sortPages);
        assertEquals(3, sortPages.size());
        assertSame(pages, sortPages);
        assertSame(leastRecentlyUsed, sortPages.get(0));
        assertSame(moreOrLessRecentlyUsed, sortPages.get(1));
        assertSame(mostRecentlyUsed, sortPages.get(2));
    }

    /**
     * In the event two pages have the same access time, the one at the higher zoom level takes
     * precedence
     */
    @SuppressWarnings("serial")
    public void testSortPagesForExpirationSameAccessTime() {
        List<TilePage> pages = new ArrayList<TilePage>();
        TilePage lowerZoomLevel = new TilePage(1, 1, 1) {
            @Override
            public int getLastAccessTimeMinutes() {
                return 1;
            }
        };
        TilePage higherZoomLevel = new TilePage(1, 1, 2) {
            @Override
            public int getLastAccessTimeMinutes() {
                return 1;
            }
        };

        pages.add(lowerZoomLevel);
        pages.add(higherZoomLevel);

        List<TilePage> sortPages = ExpirationPolicyLRU.sortPages(pages);
        assertNotNull(sortPages);
        assertEquals(2, sortPages.size());
        assertSame(pages, sortPages);
        assertSame(higherZoomLevel, sortPages.get(0));
        assertSame(lowerZoomLevel, sortPages.get(1));
    }
}
