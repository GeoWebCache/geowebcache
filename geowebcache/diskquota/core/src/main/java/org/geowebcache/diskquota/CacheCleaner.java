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
package org.geowebcache.diskquota;

import java.math.BigInteger;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.TileRange;
import org.springframework.beans.factory.DisposableBean;

/**
 * 
 * @author groldan
 * @see DiskQuotaMonitor
 */
public class CacheCleaner implements DisposableBean {

    private static final Log log = LogFactory.getLog(CacheCleaner.class);

    private final TileBreeder tileBreeder;

    private boolean shutDown;

    public static interface QuotaResolver {
        ExpirationPolicy getExpirationPolicy();

        Quota getLimit();

        Quota getUsed() throws InterruptedException;
    }

    public static class GlobalQuotaResolver implements QuotaResolver {

        private final DiskQuotaConfig config;

        private final QuotaStore store;

        public GlobalQuotaResolver(DiskQuotaConfig config, QuotaStore store) {
            this.config = config;
            this.store = store;
        }

        public Quota getLimit() {
            return config.getGlobalQuota();
        }

        public Quota getUsed() throws InterruptedException {
            return store.getGloballyUsedQuota();
        }

        public ExpirationPolicy getExpirationPolicy() {
            return config.getGlobalExpirationPolicyName();
        }
    }

    public static class LayerQuotaResolver implements QuotaResolver {
        private final LayerQuota layerQuota;

        private final QuotaStore store;

        public LayerQuotaResolver(LayerQuota layerQuota, QuotaStore store) {
            this.layerQuota = layerQuota;
            this.store = store;
        }

        public Quota getLimit() {
            Quota limit = layerQuota.getQuota();
            if (limit == null) {
                // has the admin disabled specific quota for this layer?
                limit = new Quota(BigInteger.valueOf(Long.MAX_VALUE));
            }
            return limit;
        }

        public Quota getUsed() throws InterruptedException {
            String layer = layerQuota.getLayer();
            Quota usedQuotaByLayerName = store.getUsedQuotaByLayerName(layer);
            return usedQuotaByLayerName;
        }

        public ExpirationPolicy getExpirationPolicy() {
            ExpirationPolicy expirationPolicy = layerQuota.getExpirationPolicyName();
            return expirationPolicy;
        }

    }

    /**
     * 
     * @param tileBreeder
     *            used to truncate expired pages of tiles
     */
    public CacheCleaner(final TileBreeder tileBreeder) {
        this.tileBreeder = tileBreeder;
    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        this.shutDown = true;
    }

    /**
     * This method is thread safe and will throw interrupted exception if the thread has been
     * interrupted or the {@link #destroy() shutdown hook} has been called to signal the calling
     * code of premature termination.
     * 
     * @param layerNames
     *            the layers to expire tile pages from
     * @param quotaResolver
     *            live limit and used quota to monitor until it reaches its limit
     * @throws InterruptedException
     * @see {@link org.geowebcache.diskquota.ExpirationPolicy#expireByLayerNames}
     */
    public void expireByLayerNames(final Set<String> layerNames, final QuotaResolver quotaResolver, final QuotaStore pageStore)
            throws InterruptedException {

        Quota limit;
        Quota used;
        Quota excess;

        while (true) {
            if (shutDown || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            // get it everytime in case the admin changed it while we're processsing
            limit = quotaResolver.getLimit();
            used = quotaResolver.getUsed();
            excess = used.difference(limit);
            if (excess.getBytes().compareTo(BigInteger.ZERO) <= 0) {
                log.info("Reached back Quota: " + limit.toNiceString() + " (" + used.toNiceString() + ") for layers "
                        + layerNames);
                return;
            }
            // same thing, check it every time
            ExpirationPolicy expirationPolicy = quotaResolver.getExpirationPolicy();
            if (null == expirationPolicy) {
                log.warn("Aborting disk quota enforcement task, no expiration policy defined for layers "
                        + layerNames);
                return;
            }

            TilePage tilePage = null;
            if (ExpirationPolicy.LFU.equals(expirationPolicy)) {
                tilePage = pageStore.getLeastFrequentlyUsedPage(layerNames);
            } else if (ExpirationPolicy.LRU.equals(expirationPolicy)) {
                tilePage = pageStore.getLeastRecentlyUsedPage(layerNames);
            } else {
                throw new IllegalStateException("Unrecognized expiration policy: "
                        + expirationPolicy);
            }

            if (tilePage == null) {
                limit = quotaResolver.getLimit();
                Quota usedQuota = quotaResolver.getUsed();
                if (excess.getBytes().compareTo(BigInteger.ZERO) > 0) {
                    log.warn("No more pages to expire, check if youd disk quota"
                            + " database is out of date with your blob store. Quota: "
                            + limit.toNiceString() + " used: " + usedQuota.toNiceString());
                }
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Expiring tile page " + tilePage + " based on the global "
                        + expirationPolicy + " expiration policy");
            }
            if (shutDown || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            expirePage(pageStore, tilePage);

        }
    }

    private void expirePage(QuotaStore pageStore, TilePage tilePage) throws InterruptedException {
        final String tileSetId = tilePage.getTileSetId();
        final TileSet tileSet = pageStore.getTileSetById(tileSetId);
        final String layerName = tileSet.getLayerName();
        final String gridSetId = tileSet.getGridsetId();
        final String blobFormat = tileSet.getBlobFormat();
        final String parametersId = tileSet.getParametersId();
        final int zoomLevel = tilePage.getZoomLevel();
        final long[][] pageGridCoverage = pageStore.getTilesForPage(tilePage);

        MimeType mimeType;
        try {
            mimeType = MimeType.createFromFormat(blobFormat);
        } catch (MimeException e) {
            throw new RuntimeException(e);
        }
        if (log.isTraceEnabled()) {
            if (parametersId != null) {
                log.trace("Expiring page " + tilePage + "/" + mimeType.getFormat() 
                    + "/" + parametersId);
            } else {
                log.trace("Expiring page " + tilePage + "/" + mimeType.getFormat());
            }
        }
        GWCTask truncateTask = createTruncateTaskForPage(layerName, gridSetId, zoomLevel,
                pageGridCoverage, mimeType, parametersId);

        // truncate synchronously. We're already inside the interested thread
        try {
            truncateTask.doAction();
            pageStore.setTruncated(tilePage);
        } catch (InterruptedException e) {
            log.debug("Truncate task interrupted");
            return;
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
    }

    // FRD , Long parameterId
    private GWCTask createTruncateTaskForPage(final String layerName, String gridSetId,
            int zoomLevel, long[][] pageGridCoverage, MimeType mimeType, String parametersId) {
        TileRange tileRange;
        {
            int zoomStart = zoomLevel;
            int zoomStop = zoomLevel;

            // We only need the parametersId here.
            tileRange = new TileRange(layerName, gridSetId, zoomStart, zoomStop, pageGridCoverage,
                    mimeType, null, parametersId);
        }

        boolean filterUpdate = false;
        GWCTask[] truncateTasks;
        try {
            truncateTasks = this.tileBreeder.createTasks(tileRange, GWCTask.TYPE.TRUNCATE, 1,
                    filterUpdate);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
        GWCTask truncateTask = truncateTasks[0];

        return truncateTask;
    }

}
