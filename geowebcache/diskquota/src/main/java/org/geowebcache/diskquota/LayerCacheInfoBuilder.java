package org.geowebcache.diskquota;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.diskquota.paging.TilePage;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;
import org.geowebcache.util.FileUtils;

/**
 * Gathers information about the cache of a layer, such as its size and available {@link TilePage}s.
 * 
 * @author groldan
 */
final class LayerCacheInfoBuilder {

    private static final Log log = LogFactory.getLog(LayerCacheInfoBuilder.class);

    private final File rootCacheDir;

    private final ExecutorService threadPool;

    private final int blockSize;

    private final Map<String, List<Future<ZoomLevelVisitor.Stats>>> perLayerRunningTasks;

    public LayerCacheInfoBuilder(final File rootCacheDir, final ExecutorService threadPool,
            final int blockSize) {
        this.rootCacheDir = rootCacheDir;
        this.threadPool = threadPool;
        this.blockSize = blockSize;
        this.perLayerRunningTasks = new HashMap<String, List<Future<ZoomLevelVisitor.Stats>>>();
    }

    /**
     * Asynchronously collects cache usage information for the given {@code tileLayer} into the
     * given {@code layerQuota} by using the provided {@link ExecutorService} at construction time.
     * <p>
     * This method discards any {@link LayerQuota#getUsedQuota() used quota} information available
     * for {@code layerQuota} and updates it by collecting the usage information for the layer.
     * </p>
     * <p>
     * In addition to collecting the cache usage information for the layer, the {@code layerQuota}'s
     * {@link LayerQuotaExpirationPolicy expiration policy} will be given the opportunity to gather
     * any additional information by calling the
     * {@link LayerQuotaExpirationPolicy#createInfoFor(LayerQuota, String, long[], File)
     * createInforFor(layerQuota, gridSetId, tileXYZ, file)} method for each available tile on the
     * layer's cache.
     * </p>
     * <p>
     * Note the cache information gathering is performed asynchronously and hence this method
     * returns immediately. To check whether the information collect for a given layer has finished
     * use the {@link #isRunning(String) isRunning(layerName)} method.
     * </p>
     * 
     * @param tileLayer
     * @param layerQuota
     */
    public void buildCacheInfo(final TileLayer tileLayer, final LayerQuota layerQuota) {

        final String layerName = layerQuota.getLayer();
        final String layerDirName = FilePathGenerator.filteredLayerName(layerName);

        final File layerDir = new File(rootCacheDir, layerDirName);

        // truncate the usage information before gathering the updated information
        layerQuota.getUsedQuota().setValue(0);

        perLayerRunningTasks.put(layerName, new ArrayList<Future<ZoomLevelVisitor.Stats>>());

        if (layerDir.exists()) {
            final Map<String, GridSubset> gridSubsets = tileLayer.getGridSubsets();

            for (GridSubset gs : gridSubsets.values()) {
                final String gridSetId = gs.getName();
                final int zoomStart = gs.getZoomStart();
                final int zoomStop = gs.getZoomStop();

                for (int zoomLevel = zoomStart; zoomLevel <= zoomStop; zoomLevel++) {
                    final String gridsetZLevelDirName = FilePathGenerator.gridsetZoomLevelDir(
                            gridSetId, zoomLevel);
                    final File gridsetZLevelDir = new File(layerDir, gridsetZLevelDirName);

                    if (gridsetZLevelDir.exists()) {
                        ZoomLevelVisitor cacheInfoBuilder;
                        cacheInfoBuilder = new ZoomLevelVisitor(gridsetZLevelDir, gridSetId,
                                zoomLevel, layerQuota, blockSize);

                        Future<ZoomLevelVisitor.Stats> cacheTask;
                        cacheTask = threadPool.submit(cacheInfoBuilder);

                        perLayerRunningTasks.get(layerName).add(cacheTask);
                        log.info("Submitted background task to gather cache info for '" + layerName
                                + "'/" + gridSetId + "/" + zoomLevel);
                    }
                }
            }
        }
    }

    /**
     * Builds the cache information for a zingle zoom level/gridsetId/layer combo
     * 
     * @author groldan
     * 
     */
    private static final class ZoomLevelVisitor implements FileFilter,
            Callable<ZoomLevelVisitor.Stats> {

        private final String gridSetId;

        private int tileZ;

        private final File zoomLevelPath;

        private final LayerQuota layerQuota;

        private final LayerQuotaExpirationPolicy policy;

        private final int blockSize;

        private Stats stats;

        private static class Stats {
            long runTimeMillis;

            long numTiles;

            Quota collectedQuota = new Quota();
        }

        public ZoomLevelVisitor(final File zoomLevelPath, final String gridsetId,
                final int zoomLevel, final LayerQuota layerQuota, final int blockSize) {
            this.zoomLevelPath = zoomLevelPath;
            this.gridSetId = gridsetId;
            this.layerQuota = layerQuota;
            this.blockSize = blockSize;
            this.tileZ = zoomLevel;
            this.policy = layerQuota.getExpirationPolicy();
            this.stats = new Stats();
        }

        /**
         * @see java.util.concurrent.Callable#call()
         */
        public Stats call() throws Exception {
            final String zLevelKey = layerQuota.getLayer() + "'/" + gridSetId + "/" + tileZ;
            try {
                log.debug("Gathering cache information for '" + zLevelKey);
                stats.collectedQuota.setValue(0);
                stats.numTiles = 0L;
                stats.runTimeMillis = 0L;
                long runTime = System.currentTimeMillis();
                FileUtils.traverseDepth(zoomLevelPath, this);
                runTime = System.currentTimeMillis() - runTime;
                stats.runTimeMillis = runTime;
            } catch (TraversalCanceledException cancel) {
                log.debug("Gathering cache information for " + zLevelKey + " was canceled.");
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                throw (e);
            }
            log.info("Cache information for " + zLevelKey + " collected in " + stats.runTimeMillis
                    / 1000D + "s. Counted " + stats.numTiles + " tiles for a storage space of "
                    + stats.collectedQuota.toNiceString());
            return stats;
        }

        /**
         * @see java.io.FileFilter#accept(java.io.File)
         */
        public boolean accept(final File file) {
            if (Thread.interrupted()) {
                throw new TraversalCanceledException();
            }
            if (file.isDirectory()) {
                log.trace("Processing files in " + file.getAbsolutePath());
                return true;
            }

            final long length = file.length();
            final double fileSize = blockSize * Math.ceil((double) length / blockSize);
            final Quota usedQuota = layerQuota.getUsedQuota();
            usedQuota.add(fileSize, StorageUnit.B);

            // we know path is a direct child of processingDir and represents a tile file...
            final String path = file.getPath();

            final int fileNameIdx = 1 + path.lastIndexOf(File.separatorChar);
            final int coordSepIdx = path.lastIndexOf('_');
            final int dotIdx = path.lastIndexOf('.');

            final long x = Long.valueOf(path.substring(fileNameIdx, coordSepIdx));
            final long y = Long.valueOf(path.substring(1 + coordSepIdx, dotIdx));

            policy.createInfoFor(layerQuota, gridSetId, x, y, tileZ);

            stats.numTiles++;
            stats.collectedQuota.add(fileSize, StorageUnit.B);
            return true;
        }

        /**
         * Used to brute-force cancel a cache inspection (as InterruptedException is checked and
         * hence can't use it in accept(File) above
         * 
         * @author groldan
         * 
         */
        private static class TraversalCanceledException extends RuntimeException {
            private static final long serialVersionUID = 1L;
            // doesn't need a body
        }
    }

    /**
     * Returns whether cache information is still being gathered for the layer named after {@code
     * layerName}.
     * 
     * @param layerName
     * @return {@code true} if the cache information gathering for {@code layerName} is not finished
     */
    public boolean isRunning(String layerName) {
        try {
            List<Future<ZoomLevelVisitor.Stats>> layerTasks = perLayerRunningTasks.get(layerName);
            if (layerTasks == null) {
                return false;
            }

            int numRunning = 0;
            Future<ZoomLevelVisitor.Stats> future;
            for (Iterator<Future<ZoomLevelVisitor.Stats>> it = layerTasks.iterator(); it.hasNext();) {
                future = it.next();
                if (future.isDone()) {
                    it.remove();
                } else {
                    numRunning++;
                }
            }
            return numRunning > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void shutDown() {
        this.threadPool.shutdownNow();
    }
}
