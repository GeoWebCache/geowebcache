package org.geowebcache.diskquota.lru;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.diskquota.ConfigLoader;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.diskquota.LayerQuota;
import org.geowebcache.diskquota.LayerQuotaExpirationPolicy;
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
 * Singleton bean that expects {@link Quota}s to be {@link #attach(TileLayer, Quota) attached} for
 * all monitored {@link TileLayer layers} and whips out pages of tiles - based on a sort of Least
 * Recently Used algorithm - when requested through {@link #expireTiles(String)}.
 * 
 * @author groldan
 * @see DiskQuotaMonitor
 */
public class ExpirationPolicyLRU implements LayerQuotaExpirationPolicy, DisposableBean {

    private static final Log log = LogFactory.getLog(ExpirationPolicyLRU.class);

    private static final String POLICY_NAME = "LRU";

    private final Map<String, TilePageCalculator> attachedLayers;

    private final TileBreeder tileBreeder;

    private final ConfigLoader configLoader;

    /**
     * 
     * @param tileBreeder
     *            used to truncate expired pages of tiles
     */
    public ExpirationPolicyLRU(final TileBreeder tileBreeder, final ConfigLoader configLoader) {
        this.tileBreeder = tileBreeder;
        this.configLoader = configLoader;
        attachedLayers = new HashMap<String, TilePageCalculator>();
    }

    /**
     * @see org.geowebcache.diskquota.LayerQuotaExpirationPolicy#getName()
     */
    public String getName() {
        return POLICY_NAME;
    }

    /**
     * @see org.geowebcache.diskquota.LayerQuotaExpirationPolicy#attach(org.geowebcache.layer.TileLayer,
     *      org.geowebcache.diskquota.LayerQuota)
     */
    public void attach(final TileLayer tileLayer, LayerQuota layerQuota) {
        log.info("Attaching layer '" + tileLayer.getName() + "' to cache expiration policy "
                + getName());

        TilePageCalculator calc = new TilePageCalculator(tileLayer, layerQuota);
        loadPages(calc);

        TileLayerListener statsCollector = new LRUStatsCollector(calc);
        tileLayer.addLayerListener(statsCollector);

        this.attachedLayers.put(tileLayer.getName(), calc);
    }

    /**
     * @see org.geowebcache.diskquota.LayerQuotaExpirationPolicy#dettach(java.lang.String)
     */
    public void dettach(String layerName) {
        this.attachedLayers.remove(layerName);
    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        for (TilePageCalculator calc : this.attachedLayers.values()) {
            savePages(calc);
        }
    }

    public void save(LayerQuota lq) {
        TilePageCalculator calc = this.attachedLayers.get(lq.getLayer());
        try {
            savePages(calc);
        } catch (ConfigurationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPages(TilePageCalculator calc) {
        final TileLayer tileLayer = calc.getTileLayer();
        final String layerName = FilePathGenerator.filteredLayerName(tileLayer.getName());
        final Hashtable<String, GridSubset> gridSubsets = tileLayer.getGridSubsets();
        log.info("Loading LRU state for layer '" + layerName + "'");

        for (String gridSetId : gridSubsets.keySet()) {
            String fileName = layerName + "." + FilePathGenerator.filteredGridSetId(gridSetId)
                    + ".lru";
            InputStream lruStateIn;
            try {
                lruStateIn = configLoader.getInputStream(fileName);
            } catch (Exception e) {
                log.debug(e.getMessage());
                continue;
            }

            try {
                ObjectInputStream in = new ObjectInputStream(lruStateIn);
                List<TilePage> pages = (List<TilePage>) in.readObject();
                calc.setPages(gridSetId, pages);
                log.info("LRU state for layer '" + layerName + "'" + "/" + gridSetId + " loaded.");
            } catch (Exception e) {
                log.debug(e.getMessage());
                continue;
            }
        }
    }

    private synchronized void savePages(final TilePageCalculator calc)
            throws ConfigurationException, IOException {

        final TileLayer tileLayer = calc.getTileLayer();
        final String layerName = FilePathGenerator.filteredLayerName(tileLayer.getName());
        final Hashtable<String, GridSubset> gridSubsets = tileLayer.getGridSubsets();
        log.debug("Saving LRU state for layer '" + layerName + "'");

        for (String gridSetId : gridSubsets.keySet()) {
            ArrayList<TilePage> availablePages = calc.getPages(gridSetId);
            if (availablePages.size() == 0) {
                continue;
            }
            String fileName = layerName + "." + FilePathGenerator.filteredGridSetId(gridSetId)
                    + ".lru";
            log.debug("Saving LRU state for " + layerName + "/" + gridSetId + " containing "
                    + availablePages.size() + " pages.");
            OutputStream fileOut = configLoader.getOutputStream(fileName);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            try {
                out.writeObject(availablePages);
            } finally {
                out.close();
            }
        }
        log.debug("LRU state for layer '" + layerName + "' saved.");
    }

    /**
     * Per layer LRU statistics collector
     * 
     * @author groldan
     * 
     */
    private static class LRUStatsCollector implements TileLayerListener {

        private final TilePageCalculator pageCalculator;

        /**
         * 
         * @param pageCalculator
         */
        public LRUStatsCollector(final TilePageCalculator pageCalculator) {
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
            TilePage page = pageCalculator.pageFor(tileXYZ, gridSetId);
            page.markHit();
            if (log.isTraceEnabled()) {
                log.trace("Tile requested: " + Arrays.toString(tile.getTileIndex()) + " page: "
                        + page);
            }
        }

    }

    /**
     * @throws GeoWebCacheException
     * @see org.geowebcache.diskquota.LayerQuotaExpirationPolicy#expireTiles(java.lang.String,
     *      org.geowebcache.diskquota.Quota, org.geowebcache.diskquota.Quota)
     */
    public void expireTiles(final String layerName) throws GeoWebCacheException {

        TilePageCalculator tilePageCalculator = this.attachedLayers.get(layerName);

        if (tilePageCalculator == null) {
            throw new GeoWebCacheException(layerName + " is not attached to expiration policy "
                    + getName());
        }

        final LayerQuota layerQuota = tilePageCalculator.getLayerQuota();
        final Quota quotaLimit = layerQuota.getQuota();
        final Quota usedQuota = layerQuota.getUsedQuota();
        if (usedQuota.getValue() == 0D) {
            return;
        }

        final TileLayer tileLayer = tilePageCalculator.getTileLayer();
        final Quota exceededQuota = new Quota(usedQuota);

        final Collection<GridSubset> gridSubsets = tileLayer.getGridSubsets().values();

        for (GridSubset gridSubSet : gridSubsets) {
            String gridSetId = gridSubSet.getName();
            List<TilePage> allPages = tilePageCalculator.getAllPages(gridSetId);
            Collections.sort(allPages, LRUSorter);

            for (TilePage page : allPages) {
                for (MimeType mimeType : tileLayer.getMimeTypes()) {
                    log.trace("Expiring page " + page + "/" + mimeType.getFormat());

                    final long[][] pageGridCoverage = tilePageCalculator.toGridCoverage(page,
                            gridSetId);
                    final int zoomLevel = page.getZ();
                    GWCTask truncateTask = createTruncateTaskForPage(tileLayer, gridSetId,
                            zoomLevel, pageGridCoverage, mimeType);

                    // truncate synchronously. We're already inside the interested thread
                    truncateTask.doAction();

                    // how much storage space did we freed up?
                    Quota difference = exceededQuota.difference(usedQuota);
                    if (difference.getValue() > 0) {
                        // did we reach the layer's quota?
                        Quota newExcedent = usedQuota.difference(quotaLimit);
                        double excedentValue = newExcedent.getValue();

                        if (excedentValue <= 0) {
                            log.info("Storage space for layer '" + layerName + "' reduced by "
                                    + newExcedent + " and reached its quota limit of: "
                                    + quotaLimit + ". Current usage: " + usedQuota);
                            return;
                        } else {
                            if (log.isTraceEnabled()) {
                                log.trace("After truncating page " + page + "/" + gridSetId
                                        + " for layer '" + layerName
                                        + "' its quota is still exceeded by " + newExcedent
                                        + ". Truncating more pages.");
                            }
                        }

                    } else if (difference.getValue() == 0) {
                        if (log.isTraceEnabled()) {
                            log.trace("Truncation of tile page " + page + "/" + gridSetId
                                    + " produced no reduction in storage for layer " + layerName);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Storage space for layer '" + layerName + "' increased by "
                                    + difference.getValue() + difference.getUnits()
                                    + " after truncating " + page
                                    + ". Other client requests or seeding "
                                    + "tasks might be interferring.");
                        }
                    }
                }
            }
        }

    }

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

    private static final Comparator<TilePage> LRUSorter = new Comparator<TilePage>() {

        public int compare(TilePage p1, TilePage p2) {
            // we use p1 - p2 for reverse order (ie, less hits first)
            int d = (int) (p1.getNumHits() - p2.getNumHits());
            // if (d == 0) {
            // d = p1.getZ() - p2.getZ();
            // }
            return d;
        }
    };

    public void createInfoFor(final LayerQuota layerQuota, final String gridSetId,
            final long[] tileXYZ, final File file) {
        TilePageCalculator pages = this.attachedLayers.get(layerQuota.getLayer());
        TilePage page = pages.pageFor(tileXYZ, gridSetId);
        page.addTile();
    }

}
