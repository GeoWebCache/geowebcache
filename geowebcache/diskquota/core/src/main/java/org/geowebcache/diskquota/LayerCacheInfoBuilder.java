/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FilenameUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.blobstore.file.FilePathUtils;
import org.geowebcache.util.FileUtils;

/**
 * Gathers information about the cache of a layer, such as its size and available {@link TilePage}s.
 *
 * @author groldan
 */
final class LayerCacheInfoBuilder {

    private static final Logger log = Logging.getLogger(LayerCacheInfoBuilder.class.getName());

    private final File rootCacheDir;

    private final ExecutorService threadPool;

    private final Map<String, List<Future<?>>> perLayerRunningTasks;

    private final QuotaUpdatesMonitor quotaUsageMonitor;

    private boolean closed = false;

    public LayerCacheInfoBuilder(
            final File rootCacheDir, final ExecutorService threadPool, QuotaUpdatesMonitor quotaUsageMonitor) {
        this.rootCacheDir = rootCacheDir;
        this.threadPool = threadPool;
        this.quotaUsageMonitor = quotaUsageMonitor;
        this.perLayerRunningTasks = new HashMap<>();
    }

    /**
     * Asynchronously collects cache usage information for the given {@code tileLayer} into the given {@code layerQuota}
     * by using the provided {@link ExecutorService} at construction time.
     *
     * <p>This method discards any {@link LayerQuota#getQuota()} used quota} information available for
     * {@code layerQuota} and updates it by collecting the usage information for the layer.
     *
     * <p>Note the cache information gathering is performed asynchronously and hence this method returns immediately. To
     * check whether the information collect for a given layer has finished use the {@link #isRunning(String)
     * isRunning(layerName)} method.
     */
    public void buildCacheInfo(final TileLayer tileLayer) {

        final String layerName = tileLayer.getName();
        final String layerDirName = FilePathUtils.filteredLayerName(layerName);

        final File layerDir = new File(rootCacheDir, layerDirName);

        if (!layerDir.exists()) {
            return;
        }

        perLayerRunningTasks.put(layerName, new ArrayList<>());

        // gathering the on disk tilesets can take a very long time, in case there are
        // many parameters (e.g., long list of times), so moving this task also on background exec
        Future<?> tilesetCollector = threadPool.submit(() -> gatherStatsByTileset(tileLayer, layerName, layerDir));
        // make sure the list has at this task too, so early calls to #isRunning find
        // that something is executing, even if the stats collectors have not been created yet
        perLayerRunningTasks.get(layerName).add(tilesetCollector);
    }

    private void gatherStatsByTileset(TileLayer tileLayer, String layerName, File layerDir) {
        final Set<TileSet> onDiskTileSets = findOnDiskTileSets(tileLayer, layerDir);

        for (TileSet tileSet : onDiskTileSets) {
            final String gridSetId = tileSet.getGridsetId();
            // final String blobFormat = tileSet.getBlobFormat();
            final String parametersId = tileSet.getParametersId();
            final GridSubset gs = tileLayer.getGridSubset(gridSetId);
            final int zoomStart = gs.getZoomStart();
            final int zoomStop = gs.getZoomStop();

            for (int zoomLevel = zoomStart; zoomLevel <= zoomStop && !closed; zoomLevel++) {
                String gridsetZLevelParamsDirName = FilePathUtils.gridsetZoomLevelDir(gridSetId, zoomLevel);
                if (parametersId != null) {
                    gridsetZLevelParamsDirName += "_" + parametersId;
                }
                final File gridsetZLevelDir = new File(layerDir, gridsetZLevelParamsDirName);

                if (gridsetZLevelDir.exists()) {
                    ZoomLevelVisitor cacheInfoBuilder = new ZoomLevelVisitor(
                            layerName, gridsetZLevelDir, gridSetId, zoomLevel, parametersId, quotaUsageMonitor);

                    Future<ZoomLevelVisitor.Stats> cacheTask = threadPool.submit(cacheInfoBuilder);

                    perLayerRunningTasks.get(layerName).add(cacheTask);
                    log.fine("Submitted background task to gather cache info for '"
                            + layerName
                            + "'/"
                            + gridSetId
                            + "/"
                            + zoomLevel);
                }
            }
        }
    }

    private Set<TileSet> findOnDiskTileSets(final TileLayer tileLayer, final File layerDir) {

        final String layerName = tileLayer.getName();
        final Set<String> griSetNames = tileLayer.getGridSubsets();
        Set<TileSet> foundTileSets = new HashSet<>();
        for (String gridSetName : griSetNames) {
            final String gridSetDirPrefix = FilePathUtils.filteredGridSetId(gridSetName);
            FileFilter prefixFilter = pathname -> {
                if (!pathname.isDirectory()) {
                    return false;
                }
                return pathname.getName().startsWith(gridSetDirPrefix + "_");
            };
            File[] thisGridSetDirs = FileUtils.listFilesNullSafe(layerDir, prefixFilter);
            for (File directory : thisGridSetDirs) {
                // <Filtered gridset id><_zoom level>[_<parametersId>]
                final String dirName = directory.getName();
                final String zlevelAndParamId = dirName.substring(1 + gridSetDirPrefix.length());
                final String[] parts = zlevelAndParamId.split("_");

                final String gridsetId = gridSetName;
                // we don't care here.. format should be part of the top level directory name
                final String blobFormat = null;
                String parametersId = null;
                if (parts.length == 2) {
                    parametersId = parts[1];
                }
                TileSet tileSet = new TileSet(layerName, gridsetId, blobFormat, parametersId);
                foundTileSets.add(tileSet);
            }
        }
        return foundTileSets;
    }

    /**
     * Builds the cache information for a single layer/gridsetId/parametersId/zoomLevel combo
     *
     * @author groldan
     */
    private final class ZoomLevelVisitor implements FileFilter, Callable<ZoomLevelVisitor.Stats> {

        private final String gridSetId;

        private int tileZ;

        private final File zoomLevelPath;

        private Stats stats;

        private final QuotaUpdatesMonitor quotaUsageMonitor;

        private final String layerName;

        private final String parametersId;

        private static class Stats {
            long runTimeMillis;

            long numTiles;

            Quota collectedQuota = new Quota();
        }

        public ZoomLevelVisitor(
                final String layerName,
                final File zoomLevelPath,
                final String gridsetId,
                final int zoomLevel,
                String parametersId,
                final QuotaUpdatesMonitor quotaUsageMonitor) {
            this.layerName = layerName;
            this.zoomLevelPath = zoomLevelPath;
            this.gridSetId = gridsetId;
            this.parametersId = parametersId;
            this.quotaUsageMonitor = quotaUsageMonitor;
            this.tileZ = zoomLevel;
            this.stats = new Stats();
        }

        /** @see java.util.concurrent.Callable#call() */
        @Override
        public Stats call() throws Exception {
            final String zLevelKey = layerName
                    + "'/"
                    + gridSetId
                    + "/paramId:"
                    + (parametersId == null ? "default" : parametersId)
                    + "/zlevel:"
                    + tileZ;
            try {
                log.fine("Gathering cache information for '" + zLevelKey);
                stats.numTiles = 0L;
                stats.runTimeMillis = 0L;
                long runTime = System.currentTimeMillis();
                FileUtils.traverseDepth(zoomLevelPath, this);
                runTime = System.currentTimeMillis() - runTime;
                stats.runTimeMillis = runTime;
            } catch (TraversalCanceledException cancel) {
                log.fine("Gathering cache information for " + zLevelKey + " was canceled.");
                return null;
            } catch (Exception e) {
                throw e;
            }
            log.fine("Cache information for "
                    + zLevelKey
                    + " collected in "
                    + stats.runTimeMillis / 1000D
                    + "s. Counted "
                    + stats.numTiles
                    + " tiles for a storage space of "
                    + stats.collectedQuota.toNiceString());
            return stats;
        }

        /** @see java.io.FileFilter#accept(java.io.File) */
        @Override
        public boolean accept(final File file) {
            if (closed) {
                throw new TraversalCanceledException();
            }
            if (file.isDirectory()) {
                log.finer("Processing files in " + file.getAbsolutePath());
                return true;
            }

            final long length = file.length();

            // we know path is a direct child of processingDir and represents a tile file...
            final String path = file.getPath();

            final int fileNameIdx = 1 + path.lastIndexOf(File.separatorChar);
            final int coordSepIdx = path.lastIndexOf('_');
            final int dotIdx = path.lastIndexOf('.');
            final String extension = FilenameUtils.getExtension(file.getName());
            String blobFormat;
            try {
                blobFormat = MimeType.createFromExtension(extension).getFormat();
            } catch (MimeException e) {
                throw new RuntimeException(e);
            }
            final long x = Long.valueOf(path.substring(fileNameIdx, coordSepIdx));
            final long y = Long.valueOf(path.substring(1 + coordSepIdx, dotIdx));

            this.quotaUsageMonitor.tileStored(layerName, gridSetId, blobFormat, parametersId, x, y, tileZ, length);
            stats.numTiles++;
            stats.collectedQuota.addBytes(length);
            return true;
        }

        /**
         * Used to brute-force cancel a cache inspection (as InterruptedException is checked and hence can't use it in
         * accept(File) above
         *
         * @author groldan
         */
        private static class TraversalCanceledException extends RuntimeException {
            private static final long serialVersionUID = 1L;
            // doesn't need a body
        }
    }

    /**
     * Returns whether cache information is still being gathered for the layer named after {@code layerName}.
     *
     * @return {@code true} if the cache information gathering for {@code layerName} is not finished
     */
    public boolean isRunning(String layerName) {
        try {
            List<Future<?>> layerTasks = perLayerRunningTasks.get(layerName);
            if (layerTasks == null) {
                return false;
            }

            int numRunning = 0;
            Future<?> future;
            for (Iterator<Future<?>> it = layerTasks.iterator(); it.hasNext(); ) {
                future = it.next();
                if (future.isDone()) {
                    it.remove();
                } else {
                    numRunning++;
                }
            }
            return numRunning > 0;
        } catch (Exception e) {
            log.log(Level.FINE, e.getMessage(), e);
            return false;
        }
    }

    public void shutDown() {
        this.closed = true;
        this.threadPool.shutdownNow();
    }
}
