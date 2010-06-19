package org.geowebcache.diskquota;

import static org.geowebcache.diskquota.StorageUnit.B;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * Monitors the layers cache size given each one's assigned {@link Quota} and call's the exceeded
 * layer's {@link LayerQuotaExpirationPolicy expiration policy} for cache clean up.
 * <p>
 * This monitor only cares about checking layers do not exceed their configured cache disk quota.
 * </p>
 * <p>
 * When a layer exceeds its quota, the {@link LayerQuotaExpirationPolicy} it is attached to is
 * called to {@link LayerQuotaExpirationPolicy#expireTiles(String) whip out} storage space.
 * </p>
 * 
 * @author Gabriel Roldan
 * 
 */
public class DiskQuotaMonitor implements DisposableBean {

    private static final Log log = LogFactory.getLog(DiskQuotaMonitor.class);

    private final TileLayerDispatcher tileLayerDispatcher;

    private final StorageBroker storageBroker;

    /**
     * Executor service for the periodic clean up of layers caches that exceed its quota
     * 
     * @see #setUpScheduledCleanUp()
     * @see #destroy()
     */
    private final ScheduledExecutorService cleanUpExecutorService;

    /**
     * Disk quota config object loaded and saved by {@link #configLoader}
     */
    private final DiskQuotaConfig quotaConfig;

    /**
     * Loads and saves quota limits and quota usage status for configured layers
     */
    private final ConfigLoader configLoader;

    private final LayerCacheInfoBuilder cacheInfoBuilder;

    /**
     * 
     * @param configLoader
     *            loads and saves the layers quota config and usage status
     * @param tld
     *            provides access to the layers configured for disk quota insurance
     * @param sb
     *            provides a mean to listen to {@link BlobStore} events to keep track of layers disk
     *            quota usage
     * @throws IOException
     * @throws ConfigurationException
     */
    public DiskQuotaMonitor(final ConfigLoader configLoader, final TileLayerDispatcher tld,
            final StorageBroker sb) throws IOException, ConfigurationException {

        this.configLoader = configLoader;
        this.storageBroker = sb;
        this.tileLayerDispatcher = tld;

        this.quotaConfig = configLoader.loadConfig();

        if (quotaConfig.getNumLayers() == 0) {
            this.cleanUpExecutorService = null;
            this.cacheInfoBuilder = null;
            log.info("No layer quotas defined. Disk quota monitor is disabled.");
        } else {
            this.cleanUpExecutorService = createCleanUpExecutor();

            attachConfiguredLayers();

            this.cacheInfoBuilder = launchCacheInfoGatheringThreads();

            final MonitoringBlobListener blobListener = new MonitoringBlobListener(quotaConfig);
            storageBroker.addBlobStoreListener(blobListener);

            setUpScheduledCleanUp();

            int totalLayers = tileLayerDispatcher.getLayers().size();
            int quotaLayers = quotaConfig.getNumLayers();
            log.info(quotaLayers + " out of " + totalLayers
                    + " layers configured with their own quotas.");
        }
    }

    /**
     * Called when the framework destroys this bean (e.g. due to web app shutdown), stops any
     * running scheduled clean up and gracefuly shuts down
     * 
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        if (this.cleanUpExecutorService != null) {
            this.cleanUpExecutorService.shutdown();
        }
        if(this.cacheInfoBuilder != null){
            this.cacheInfoBuilder.shutDown();
        }
        try {
            log.info("Disk quota monitor shutting down, saving configuration");
            configLoader.saveConfig(quotaConfig);
            log.info("Disk quota configuration saved.");
        } catch (Exception e) {
            log.error("Error saving disk quota config: " + e.getMessage(), e);
        }
    }

    private LayerCacheInfoBuilder launchCacheInfoGatheringThreads() throws StorageException {

        final File rootCacheDir = configLoader.getRootCacheDir();
        final int blockSize = quotaConfig.getDiskBlockSize();

        LayerCacheInfoBuilder cacheInfoBuilder;
        cacheInfoBuilder = new LayerCacheInfoBuilder(rootCacheDir, cleanUpExecutorService,
                blockSize);

        TileLayer tileLayer;
        for (LayerQuota layerQuota : quotaConfig.getLayerQuotas()) {
            try {
                tileLayer = this.tileLayerDispatcher.getTileLayer(layerQuota.getLayer());
            } catch (GeoWebCacheException e) {
                throw new RuntimeException(e);
            }
            cacheInfoBuilder.buildCacheInfo(tileLayer, layerQuota);
        }
        return cacheInfoBuilder;
    }

    private ScheduledExecutorService createCleanUpExecutor() {

        final int numCleaningThreads = quotaConfig.getMaxConcurrentCleanUps();
        log.info("Setting up disk quota periodic enforcement task");
        CustomizableThreadFactory tf = new CustomizableThreadFactory("gwc.DiskQuotaCleanUpThread");
        tf.setThreadPriority(1 + (Thread.MAX_PRIORITY - Thread.MIN_PRIORITY) / 5);

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(
                numCleaningThreads, tf);
        return executorService;
    }

    private void setUpScheduledCleanUp() {

        Runnable command = new CacheCleaner(configLoader, quotaConfig, cacheInfoBuilder,
                cleanUpExecutorService);

        long initialDelay = quotaConfig.getCacheCleanUpFrequency();
        long period = quotaConfig.getCacheCleanUpFrequency();
        TimeUnit unit = quotaConfig.getCacheCleanUpUnits();
        cleanUpExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
        log.info("Disk quota periodic enforcement task set up every " + period + " " + unit);
    }

    /**
     * Attaches each {@link LayerQuota} to its {@link LayerQuotaExpirationPolicy} by first looking
     * for the layer's declared expiration policy implementation and then calling
     * {@link LayerQuotaExpirationPolicy#attach(TileLayer, LayerQuota)}
     */
    private void attachConfiguredLayers() {

        Map<String, TileLayer> layers;
        layers = new HashMap<String, TileLayer>(tileLayerDispatcher.getLayers());
        List<LayerQuota> layerQuotas = quotaConfig.getLayerQuotas();

        for (LayerQuota layerQuota : layerQuotas) {

            final String layerName = layerQuota.getLayer();
            Quota quota = layerQuota.getQuota();
            final String policyName = layerQuota.getExpirationPolicyName();

            log.info("Attaching layer " + layerName + " to quota " + quota);

            final LayerQuotaExpirationPolicy expirationPolicy;
            expirationPolicy = configLoader.findExpirationPolicy(policyName);
            layerQuota.setExpirationPolicy(expirationPolicy);

            TileLayer tileLayer = layers.get(layerName);
            expirationPolicy.attach(tileLayer, layerQuota);
            layers.remove(layerName);
        }
    }

    private static class MonitoringBlobListener implements BlobStoreListener {

        private final DiskQuotaConfig quotaConfig;

        public MonitoringBlobListener(final DiskQuotaConfig quotaConfig) {
            this.quotaConfig = quotaConfig;
        }

        /**
         * @see org.geowebcache.storage.BlobStoreListener#tileStored(java.lang.String,
         *      java.lang.String, java.lang.String, java.lang.String, long, long, int, long)
         */
        public void tileStored(final String layerName, final String gridSetId,
                final String blobFormat, final String parameters, final long x, final long y,
                final int z, final long blobSize) {

            final LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
            if (layerQuota == null) {
                // there's no quota defined for the layer
                return;
            }
            final int blockSize = quotaConfig.getDiskBlockSize();

            long actuallyUsedStorage = blockSize * (int) Math.ceil((double) blobSize / blockSize);

            Quota usedQuota = layerQuota.getUsedQuota();

            usedQuota.add(actuallyUsedStorage, B);

            // inform the layer policy the tile has been added, in case it needs that information
            LayerQuotaExpirationPolicy policy = layerQuota.getExpirationPolicy();
            policy.createInfoFor(layerQuota, gridSetId, x, y, z);

            // mark the config as dirty so its saved when appropriate
            quotaConfig.setDirty(true);
            layerQuota.setDirty(true);
            if (log.isDebugEnabled()) {
                log.debug("Used quota increased for " + layerName + ": " + usedQuota);
            }
        }

        /**
         * @see org.geowebcache.storage.BlobStoreListener#tileDeleted(java.lang.String,
         *      java.lang.String, java.lang.String, java.lang.String, long, long, int, long)
         */
        public void tileDeleted(final String layerName, final String gridSetId,
                final String blobFormat, final String parameters, final long x, final long y,
                final int z, final long blobSize) {

            final LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
            if (layerQuota == null) {
                // there's no quota defined for the layer
                return;
            }
            int blockSize = quotaConfig.getDiskBlockSize();

            long actualTileSizeOnDisk = blockSize * (int) Math.ceil((double) blobSize / blockSize);

            Quota usedQuota = layerQuota.getUsedQuota();

            usedQuota.subtract(actualTileSizeOnDisk, B);

            // inform the layer policy the tile has been deleted, in case it needs that information
            LayerQuotaExpirationPolicy policy = layerQuota.getExpirationPolicy();
            policy.removeInfoFor(layerQuota, gridSetId, x, y, z);

            // mark the config as dirty so its saved when appropriate
            quotaConfig.setDirty(true);
            layerQuota.setDirty(true);
            if (log.isTraceEnabled()) {
                log.trace("Used quota decreased for " + layerName + ": " + usedQuota);
            }
        }

        /**
         * @see org.geowebcache.storage.BlobStoreListener#layerDeleted(java.lang.String)
         */
        public void layerDeleted(final String layerName) {
            final LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
            if (layerQuota == null) {
                // there's no quota defined for the layer
                return;
            }
            LayerQuotaExpirationPolicy expirationPolicy = layerQuota.getExpirationPolicy();
            expirationPolicy.dettach(layerName);
            quotaConfig.remove(quotaConfig.getLayerQuota(layerName));
            // mark the config as dirty so its saved when appropriate
            quotaConfig.setDirty(true);
        }
    }

    private static class CacheCleaner implements Runnable {

        private final DiskQuotaConfig quotaConfig;

        private final ExecutorService cleanUpExecutorService;

        private final Map<String, Future<?>> perLayerRunningCleanUps;

        private final LayerCacheInfoBuilder cacheInfoBuilder;

        private final ConfigLoader configLoader;

        public CacheCleaner(final ConfigLoader configLoader, final DiskQuotaConfig config,
                final LayerCacheInfoBuilder cacheInfoBuilder, final ExecutorService executor) {

            this.configLoader = configLoader;
            this.quotaConfig = config;
            this.cleanUpExecutorService = executor;
            this.cacheInfoBuilder = cacheInfoBuilder;
            this.perLayerRunningCleanUps = new HashMap<String, Future<?>>();
        }

        public void run() {
            // first, save the config to account for changes in used quotas
            if (quotaConfig.isDirty()) {
                try {
                    configLoader.saveConfig(quotaConfig);
                    quotaConfig.setDirty(false);
                } catch (Exception e) {
                    log.error("Error saving disk quota config", e);
                }
            }

            for (LayerQuota lq : quotaConfig.getLayerQuotas()) {
                final String layerName = lq.getLayer();

                if (cacheInfoBuilder.isRunning(layerName)) {
                    log.info("Cache information is still being gathered for layer '" + layerName
                            + "'. Skipping quota enforcement task for this layer.");
                    continue;
                }

                Future<?> runningCleanup = perLayerRunningCleanUps.get(layerName);
                if (runningCleanup != null && !runningCleanup.isDone()) {
                    log.debug("Cache clean up task still running for layer '" + layerName
                            + "'. Ignoring it for this run.");
                    continue;
                }

                final Quota quota = lq.getQuota();
                final Quota usedQuota = lq.getUsedQuota();
                LayerQuotaExpirationPolicy expirationPolicy = lq.getExpirationPolicy();
                if (lq.isDirty()) {
                    expirationPolicy.save(layerName);
                    lq.setDirty(false);
                }

                Quota excedent = usedQuota.difference(quota);
                if (excedent.getValue().compareTo(BigDecimal.ZERO) > 0) {
                    log.info("Layer '" + lq.getLayer() + "' exceeds its quota of "
                            + quota.toNiceString() + " by " + excedent.toNiceString()
                            + ". Currently used: " + usedQuota.toNiceString()
                            + ". Clean up task is gonna be performed" + " using expiration policy "
                            + lq.getExpirationPolicyName());

                    LayerQuotaEnforcementTask task;
                    task = new LayerQuotaEnforcementTask(lq);
                    Future<Object> future = this.cleanUpExecutorService.submit(task);
                    perLayerRunningCleanUps.put(layerName, future);
                }
            }
        }
    }

    private static class LayerQuotaEnforcementTask implements Callable<Object> {

        private final LayerQuota layerQuota;

        public LayerQuotaEnforcementTask(final LayerQuota layerQuota) {
            this.layerQuota = layerQuota;
        }

        public Object call() throws Exception {
            try {
                final String layerName = layerQuota.getLayer();
                LayerQuotaExpirationPolicy expirationPolicy = layerQuota.getExpirationPolicy();
                expirationPolicy.expireTiles(layerName);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
            return null;
        }
    }
}
