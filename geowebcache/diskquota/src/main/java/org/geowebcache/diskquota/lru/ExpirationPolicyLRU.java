package org.geowebcache.diskquota.lru;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.diskquota.Quota;
import org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy;
import org.geowebcache.diskquota.paging.PageStore;
import org.geowebcache.diskquota.paging.TilePage;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.seed.TileBreeder;

/**
 * Singleton bean that expects {@link Quota}s to be {@link #attach(TileLayer, Quota) attached} for
 * all monitored {@link TileLayer layers} and whips out pages of tiles - based on a sort of Least
 * Recently Used algorithm - when requested through {@link #expireTiles(String)}.
 * 
 * @author groldan
 * @see DiskQuotaMonitor
 */
public class ExpirationPolicyLRU extends AbstractPagedExpirationPolicy {

    private static final String POLICY_NAME = "LRU";

    /**
     * 
     * @param tileBreeder
     *            used to truncate expired pages of tiles
     */
    public ExpirationPolicyLRU(final TileBreeder tileBreeder, final PageStore pageStore) {
        super(tileBreeder, pageStore);
    }

    @Override
    public String getName() {
        return POLICY_NAME;
    }

    @Override
    protected List<TilePage> sortPagesForExpiration(List<TilePage> allPages) {
        return sortPages(allPages);
    }

    static List<TilePage> sortPages(List<TilePage> allPages) {
        Collections.sort(allPages, LRUSorter);
        return allPages;
    }

    /**
     * Comparator used to sort {@link TilePage}s in Least Recently Used order
     * 
     * @see TilePage#getLastAccessTimeMinutes()
     */
    private static final Comparator<TilePage> LRUSorter = new Comparator<TilePage>() {

        /**
         * Compares the two TilePages last access time such that the one with a more recent access
         * time is ordered after the one with a less recent access time.
         * <p>
         * In the event that both pages have the same access time (with minute precission) the one
         * at the higher zoom level will take precedence, so that we remove tiles at higher zoom
         * levels first.
         * </p>
         * 
         * @param p1
         * @param p2
         * @return
         */
        public int compare(TilePage p1, TilePage p2) {
            int p1AccessTime = p1.getLastAccessTimeMinutes();
            int p2AccessTime = p2.getLastAccessTimeMinutes();
            // we use p1 - p2 for reverse ordering (ie, least recently used first)
            int delta = p1AccessTime - p2AccessTime;
            if (delta == 0) {
                // now use p2 - p1 so the higher zoom level goes first
                delta = p2.getZ() - p1.getZ();
            }

            return delta;
        }
    };

}
