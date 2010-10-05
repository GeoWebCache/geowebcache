package org.geowebcache.diskquota.lru;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import org.geowebcache.diskquota.paging.TilePage;

public class ExpirationPolicyLRUTest extends TestCase {

    @SuppressWarnings("serial")
    public void testSortPagesForExpiration() {
        List<TilePage> pages = new ArrayList<TilePage>();

        TilePage mostRecentlyUsed = new TilePage("testLayer", "EPSG:4326", 1, 1, 1, 1, 10, 1000);
        TilePage leastRecentlyUsed = new TilePage("testLayer", "EPSG:4326", 0, 0, 0, 1, 100, 100);
        TilePage moreOrLessRecentlyUsed = new TilePage("testLayer", "EPSG:4326", 0, 0, 1, 1, 100, 500);

        mostRecentlyUsed.markHit();

        pages.add(mostRecentlyUsed);
        pages.add(leastRecentlyUsed);
        pages.add(moreOrLessRecentlyUsed);

        Collections.sort(pages, ExpirationPolicyLRU.LRUSorter);
        assertNotNull(pages);
        assertEquals(3, pages.size());
        assertSame(pages, pages);
        assertSame(leastRecentlyUsed, pages.get(0));
        assertSame(moreOrLessRecentlyUsed, pages.get(1));
        assertSame(mostRecentlyUsed, pages.get(2));
    }

    /**
     * In the event two pages have the same access time, the one at the higher zoom level takes
     * precedence
     */
    @SuppressWarnings("serial")
    public void testSortPagesForExpirationSameAccessTime() {
        List<TilePage> pages = new ArrayList<TilePage>();
        TilePage lowerZoomLevel = new TilePage("testLayer", "EPSG:4326", 1, 1, 1) {
            @Override
            public int getLastAccessTimeMinutes() {
                return 1;
            }
        };
        TilePage higherZoomLevel = new TilePage("testLayer", "EPSG:4326", 1, 1, 2) {
            @Override
            public int getLastAccessTimeMinutes() {
                return 1;
            }
        };

        pages.add(lowerZoomLevel);
        pages.add(higherZoomLevel);

        Collections.sort(pages, ExpirationPolicyLRU.LRUSorter);
        assertNotNull(pages);
        assertEquals(2, pages.size());
        assertSame(pages, pages);
        assertSame(higherZoomLevel, pages.get(0));
        assertSame(lowerZoomLevel, pages.get(1));
    }
}
