package org.geowebcache.diskquota.paging;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.diskquota.ExpirationPolicy;
import org.geowebcache.diskquota.LayerQuota;
import org.geowebcache.diskquota.Quota;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerListener;
import org.geowebcache.mime.MimeType;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;
import org.springframework.beans.factory.DisposableBean;

/**
 * Abstract base bean that expects {@link Quota}s to be {@link #attach(TileLayer, Quota) attached}
 * for all monitored {@link TileLayer layers} and whips out pages of tiles. Subclasses must
 * implement the actual expiration policy (e.g. LRU/LFU/FIFO) when requested through
 * {@link #expireTiles(String)}.
 * 
 * @author groldan
 * @see DiskQuotaMonitor
 */
public abstract class AbstractPagedExpirationPolicy implements ExpirationPolicy, DisposableBean {

    private static final Log log = LogFactory.getLog(AbstractPagedExpirationPolicy.class);

    private final Map<String, TilePageCalculator> attachedLayers;

    private final Map<String, PagingStatsCollector> statsCollectors;

    private final TileBreeder tileBreeder;

    private final PageStore pageStore;

    /**
     * 
     * @param tileBreeder
     *            used to truncate expired pages of tiles
     */
    public AbstractPagedExpirationPolicy(final TileBreeder tileBreeder, final PageStore pageStore) {
        this.tileBreeder = tileBreeder;
        this.pageStore = pageStore;
        attachedLayers = new ConcurrentHashMap<String, TilePageCalculator>();
        statsCollectors = new ConcurrentHashMap<String, PagingStatsCollector>();
    }

    /**
     * @see org.geowebcache.diskquota.ExpirationPolicy#getName()
     */
    public abstract String getName();

    /**
     * @see org.geowebcache.diskquota.ExpirationPolicy#attach(org.geowebcache.layer.TileLayer,
     *      org.geowebcache.diskquota.LayerQuota)
     */
    public void attach(final TileLayer tileLayer, LayerQuota layerQuota) {
        log.info("Attaching layer '" + tileLayer.getName() + "' to cache expiration policy "
                + getName());

        TilePageCalculator calc = new TilePageCalculator(tileLayer, layerQuota);
        loadPages(calc);

        PagingStatsCollector statsCollector = new PagingStatsCollector(calc);
        tileLayer.addLayerListener(statsCollector);

        this.attachedLayers.put(tileLayer.getName(), calc);
        this.statsCollectors.put(tileLayer.getName(), statsCollector);
    }

    /**
     * @see org.geowebcache.diskquota.ExpirationPolicy#dettach(java.lang.String)
     */
    public synchronized boolean dettach(String layerName) {
        TilePageCalculator pageCalc = this.attachedLayers.remove(layerName);
        if (pageCalc != null) {
            PagingStatsCollector statsCollector = this.statsCollectors.remove(layerName);
            TileLayer layer = pageCalc.getTileLayer();
            return layer.removeLayerListener(statsCollector);
        }
        return false;
    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        Set<String> layerNames = new HashSet<String>(this.attachedLayers.keySet());
        for (TilePageCalculator calc : this.attachedLayers.values()) {
            savePages(calc);
        }
        for (String layerName : layerNames) {
            dettach(layerName);
        }
    }

    /**
     * @see org.geowebcache.diskquota.ExpirationPolicy#save
     */
    public void save(final String layer) {
        TilePageCalculator calc = this.attachedLayers.get(layer);
        if (calc == null) {
            throw new IllegalArgumentException("No layer named '" + layer
                    + "' is attached to this expiration policy");
        }
        try {
            savePages(calc);
        } catch (IOException e) {
            log.error(e);
        }
    }

    private void loadPages(TilePageCalculator calc) {
        final TileLayer tileLayer = calc.getTileLayer();
        final String layerName = FilePathGenerator.filteredLayerName(tileLayer.getName());
        final Hashtable<String, GridSubset> gridSubsets = tileLayer.getGridSubsets();
        log.info("Loading stats pages for layer '" + layerName + "'");

        List<TilePage> pages;
        for (String gridSetId : gridSubsets.keySet()) {
            try {
                pages = pageStore.getPages(layerName, gridSetId);
            } catch (IOException e) {
                log.debug(e.getMessage());
                continue;
            }
            calc.setPages(gridSetId, pages);
        }
    }

    private synchronized void savePages(final TilePageCalculator calc) throws IOException {

        final TileLayer tileLayer = calc.getTileLayer();
        final String layerName = FilePathGenerator.filteredLayerName(tileLayer.getName());
        final Hashtable<String, GridSubset> gridSubsets = tileLayer.getGridSubsets();
        log.debug("Saving tile pages state for layer '" + layerName + "'");

        for (String gridSetId : gridSubsets.keySet()) {
            ArrayList<TilePage> availablePages = calc.getPages(gridSetId);
            try {
                pageStore.savePages(layerName, gridSetId, availablePages);
            } catch (IOException e) {
                log.info(e);
                continue;
            }
        }
        log.debug("Paged state for layer '" + layerName + "' saved.");
    }

    /**
     * Per layer statistics collector that groups tile stats into pages of tiles stats
     * 
     * @author groldan
     * 
     */
    private static class PagingStatsCollector implements TileLayerListener {

        private final TilePageCalculator pageCalculator;

        /**
         * 
         * @param pageCalculator
         */
        public PagingStatsCollector(final TilePageCalculator pageCalculator) {
            this.pageCalculator = pageCalculator;
        }

        /**
         * @see org.geowebcache.layer.TileLayerListener#tileRequested(org.geowebcache.layer.TileLayer,
         *      org.geowebcache.conveyor.ConveyorTile)
         */
        public void tileRequested(TileLayer layer, ConveyorTile tile) {
            long[] tileXYZ = tile.getTileIndex();
            String gridSetId = tile.getGridSetId();
            // String parameters = tile.getParameters();
            // String storageFormat = tile.getMimeType().getFormat();
            // TODO: discriminate by parameters Id? format?
            TilePage page = pageCalculator.pageFor(tileXYZ[0], tileXYZ[1], (int) tileXYZ[2],
                    gridSetId);
            page.markHit();
            if (log.isTraceEnabled()) {
                log.trace("Tile requested: " + Arrays.toString(tile.getTileIndex()) + " page: "
                        + page);
            }
        }

    }

    /**
     * @throws GeoWebCacheException
     * @see org.geowebcache.diskquota.ExpirationPolicy#expireTiles(java.lang.String,
     *      org.geowebcache.diskquota.Quota, org.geowebcache.diskquota.Quota)
     */
    public void expireTiles(final String layerName) throws GeoWebCacheException {

        final TilePageCalculator tilePageCalculator = this.attachedLayers.get(layerName);

        if (tilePageCalculator == null) {
            throw new GeoWebCacheException(layerName + " is not attached to expiration policy "
                    + getName());
        }

        Quota exceededQuota;
        final LayerQuota layerQuota = tilePageCalculator.getLayerQuota();
        final Quota quotaLimit = layerQuota.getQuota();
        final Quota usedQuota = layerQuota.getUsedQuota();

        exceededQuota = usedQuota.difference(quotaLimit);
        if (exceededQuota.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        final TileLayer tileLayer = tilePageCalculator.getTileLayer();
        final Collection<GridSubset> gridSubsets = tileLayer.getGridSubsets().values();

        /*
         * Keep in mind that a seeding process might be ongoing while we try to enforce the layer's
         * quota and the two processes may compete. We can't just ask for and sort the list of pages
         * once as they might be changing under our feet.
         */
        while (exceededQuota.getValue().compareTo(BigDecimal.ZERO) > 0) {
            // make a one-page-cleanup per gridSubset so the clean up is sort of evenly spread over
            // the different gridsets instead of whiping out too much of one and nothing of the
            // other
            for (GridSubset gridSubSet : gridSubsets) {
                final String gridSetId = gridSubSet.getName();
                List<TilePage> gsPages = tilePageCalculator.getPages(gridSetId);
                gsPages = sortPagesForExpiration(gsPages);

                TilePage tilePage = null;
                long numTilesInPage = 0;
                for (TilePage page : gsPages) {
                    numTilesInPage = page.getNumTilesInPage();
                    if (numTilesInPage > 0) {
                        tilePage = page;
                        break;
                    }
                }
                if (numTilesInPage == 0) {
                    log.warn("Didn't find a page with tiles to truncate for '" + layerName
                            + "' whilst it reports having a quota excedednt of " + exceededQuota);
                    break;
                }

                final int zoomLevel = tilePage.getZ();
                final long[][] pageGridCoverage;
                pageGridCoverage = tilePageCalculator.toGridCoverage(tilePage, gridSetId);
                for (MimeType mimeType : tileLayer.getMimeTypes()) {
                    log.trace("Expiring page " + tilePage + "/" + mimeType.getFormat());

                    GWCTask truncateTask = createTruncateTaskForPage(tileLayer, gridSetId,
                            zoomLevel, pageGridCoverage, mimeType);

                    // truncate synchronously. We're already inside the interested thread
                    truncateTask.doAction();

                    Quota newExcedent = logDifference(layerName, quotaLimit, usedQuota,
                            exceededQuota, tilePage, numTilesInPage);

                    // usedQuota may have changed
                    exceededQuota = newExcedent;
                    if (0 == tilePage.getNumTilesInPage()) {
                        // already truncated the tiles for the page at all available mime types
                        break;
                    }
                }

            }
        }
        log.debug("Quota for layer '" + layerName + "' reached. Using " + usedQuota
                + " out of a limit of " + quotaLimit);
    }

    /**
     * Truncates at least {@code truncateLimit} from the given layer
     * 
     * @param layerName
     *            the layer to truncate from
     * @param truncateLimit
     *            the amount to truncate
     * @throws GeoWebCacheException
     */
    public void expireTiles(final String layerName, final Quota truncateLimit)
            throws GeoWebCacheException {

        final TilePageCalculator tilePageCalculator = this.attachedLayers.get(layerName);

        if (tilePageCalculator == null) {
            throw new GeoWebCacheException(layerName + " is not attached to expiration policy "
                    + getName());
        }

        Quota exceededQuota;
        final LayerQuota layerQuota = tilePageCalculator.getLayerQuota();
        final Quota usedQuota = layerQuota.getUsedQuota();

        exceededQuota = usedQuota.min(truncateLimit);
        final Quota initialUsage = new Quota(usedQuota);
        if (exceededQuota.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        final TileLayer tileLayer = tilePageCalculator.getTileLayer();
        final Collection<GridSubset> gridSubsets = tileLayer.getGridSubsets().values();

        /*
         * Keep in mind that a seeding process might be ongoing while we try to enforce the layer's
         * quota and the two processes may compete. We can't just ask for and sort the list of pages
         * once as they might be changing under our feet.
         */
        while (exceededQuota.getValue().compareTo(BigDecimal.ZERO) > 0) {
            // make a one-page-cleanup per gridSubset so the clean up is sort of evenly spread over
            // the different gridsets instead of whiping out too much of one and nothing of the
            // other
            for (GridSubset gridSubSet : gridSubsets) {
                final String gridSetId = gridSubSet.getName();
                List<TilePage> gsPages = tilePageCalculator.getPages(gridSetId);
                gsPages = sortPagesForExpiration(gsPages);

                TilePage tilePage = null;
                long numTilesInPage = 0;
                for (TilePage page : gsPages) {
                    numTilesInPage = page.getNumTilesInPage();
                    if (numTilesInPage > 0) {
                        tilePage = page;
                        break;
                    }
                }
                if (numTilesInPage == 0) {
                    log.warn("Didn't find a page with tiles to truncate for '" + layerName
                            + "' whilst it reports having a quota excedednt of " + exceededQuota);
                    break;
                }

                final int zoomLevel = tilePage.getZ();
                final long[][] pageGridCoverage;
                pageGridCoverage = tilePageCalculator.toGridCoverage(tilePage, gridSetId);
                for (MimeType mimeType : tileLayer.getMimeTypes()) {
                    log.trace("Expiring page " + tilePage + "/" + mimeType.getFormat());

                    GWCTask truncateTask = createTruncateTaskForPage(tileLayer, gridSetId,
                            zoomLevel, pageGridCoverage, mimeType);

                    // truncate synchronously. We're already inside the interested thread
                    truncateTask.doAction();

                    // usedQuota changed dynamically as tiles are added/removed from the layer,
                    // initialUsage is our static reference point
                    exceededQuota = usedQuota.difference(initialUsage);

                    if (0 == tilePage.getNumTilesInPage()) {
                        // already truncated the tiles for the page at all available mime types
                        break;
                    }
                }

            }
        }
    }

    private Quota logDifference(final String layerName, final Quota quotaLimit,
            final Quota usedQuota, Quota exceededQuota, final TilePage tilePage, long numTilesInPage) {
        Quota newExcedent = usedQuota.difference(quotaLimit);
        if (log.isTraceEnabled()) {
            Quota truncated = exceededQuota.difference(newExcedent);
            log.trace("Truncated " + truncated.toNiceString() + " from page " + tilePage.getX()
                    + "," + tilePage.getY() + "," + tilePage.getZ() + ". Layer: '" + layerName
                    + "'. Tiles in page: " + tilePage.getNumTilesInPage() + ". Previously: "
                    + numTilesInPage);
        }
        return newExcedent;
    }

    protected abstract List<TilePage> sortPagesForExpiration(List<TilePage> allPages);

    private GWCTask createTruncateTaskForPage(final TileLayer tileLayer, String gridSetId,
            int zoomLevel, long[][] pageGridCoverage, MimeType mimeType)
            throws GeoWebCacheException {
        TileRange tileRange;
        {
            String layerName = tileLayer.getName();
            int zoomStart = zoomLevel;
            int zoomStop = zoomLevel;

            String parameters = null;

            tileRange = new TileRange(layerName, gridSetId, zoomStart, zoomStop, pageGridCoverage,
                    mimeType, parameters);
        }

        boolean filterUpdate = false;
        GWCTask[] truncateTasks = this.tileBreeder.createTasks(tileRange, tileLayer,
                GWCTask.TYPE.TRUNCATE, 1, filterUpdate);
        GWCTask truncateTask = truncateTasks[0];

        return truncateTask;
    }

    /**
     * @see ExpirationPolicy#createInfoFor
     */
    public void createInfoFor(final LayerQuota layerQuota, final String gridSetId, final long x,
            final long y, final int z) {
        TilePageCalculator pages = this.attachedLayers.get(layerQuota.getLayer());
        TilePage page = pages.pageFor(x, y, z, gridSetId);
        page.addTile();
    }

    public void removeInfoFor(LayerQuota layerQuota, String gridSetId, long x, long y, int z) {
        TilePageCalculator pages = this.attachedLayers.get(layerQuota.getLayer());
        TilePage page = pages.pageFor(x, y, z, gridSetId);
        page.removeTile();
    }
}
