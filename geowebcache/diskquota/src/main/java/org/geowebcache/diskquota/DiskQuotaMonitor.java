package org.geowebcache.diskquota;

import static org.geowebcache.diskquota.StorageUnit.B;
import static org.geowebcache.diskquota.StorageUnit.GB;
import static org.geowebcache.diskquota.StorageUnit.MB;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * Monitors the layers cache size given each one's assigned {@link Quota} and call's the exceeded
 * layer's {@link LayerQuotaExpirationPolicy expiration policy} for cache clean up.
 * <p>
 * This monitor only cares about checking layers does not exceed its configured (or global default,
 * if available) cache disk quota.
 * </p>
 * <p>
 * When a layer exceeds its quota, the {@link LayerQuotaExpirationPolicy} it is attached to is
 * called to whip out storage space.
 * </p>
 * 
 * @author Gabriel Roldan
 * 
 */
public class DiskQuotaMonitor implements DisposableBean {

    private static final Log log = LogFactory.getLog(DiskQuotaMonitor.class);

    private final TileLayerDispatcher tileLayerDispatcher;

    private final StorageBroker storageBroker;

    private DiskQuotaConfig quotaConfig;

    /**
     * Executor service for the periodic clean up of layers caches that exceed its quota
     * 
     * @see #setUpScheduledCleanUp()
     * @see #destroy()
     */
    private ScheduledExecutorService cleanUpExecutorService;

    public DiskQuotaMonitor(ConfigLoader configLoader, TileLayerDispatcher tld, StorageBroker sb)
            throws IOException, ConfigurationException {

        this.storageBroker = sb;
        this.tileLayerDispatcher = tld;

        this.quotaConfig = configLoader.loadConfig();
        registerConfiguredLayers(configLoader);

        if (quotaConfig.getNumLayers() == 0) {
            log.info("No layer quotas defined. Disk quota monitor is disabled.");
        } else {

            final MonitoringBlobListener blobListener = new MonitoringBlobListener(quotaConfig);

            storageBroker.addBlobStoreListener(blobListener);

            setUpScheduledCleanUp(configLoader);

            int totalLayers = tileLayerDispatcher.getLayers().size();
            int quotaLayers = quotaConfig.getNumLayers();
            log.info(quotaLayers + " out of " + totalLayers
                    + " layers configured with their own quotas.");
        }
    }

    private void setUpScheduledCleanUp(final ConfigLoader configLoader) {

        log.info("Setting up disk quota periodic enforcement task");
        ThreadFactory tf = new CustomizableThreadFactory("gwc.DiskQuotaCleanUpThread");

        int numCleaningThreads = quotaConfig.getMaxConcurrentCleanUps();

        cleanUpExecutorService = Executors.newScheduledThreadPool(numCleaningThreads, tf);

        Runnable command = new CacheCleaner(configLoader, quotaConfig, cleanUpExecutorService);

        long initialDelay = quotaConfig.getCacheCleanUpFrequency();
        long period = quotaConfig.getCacheCleanUpFrequency();
        TimeUnit unit = quotaConfig.getCacheCleanUpUnits();
        cleanUpExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
        log.info("Disk quota periodic enforcement task set up every " + period + " " + unit);
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
    }

    private void registerConfiguredLayers(final ConfigLoader configLoader) {

        Map<String, TileLayer> layers;
        layers = new HashMap<String, TileLayer>(tileLayerDispatcher.getLayers());
        List<LayerQuota> layerQuotas = quotaConfig.getLayerQuotas();

        for (LayerQuota layerQuota : layerQuotas) {

            final String layerName = layerQuota.getLayer();
            Quota quota = layerQuota.getQuota();
            final String policyName = layerQuota.getExpirationPolicyName();

            log.info("Attaching layer " + layerName + " to quota " + quota);

            final LayerQuotaExpirationPolicy expirationPolicy;
            expirationPolicy = configLoader.getExpirationPolicy(policyName);
            layerQuota.setExpirationPolicy(expirationPolicy);

            TileLayer tileLayer = layers.get(layerName);
            expirationPolicy.attach(tileLayer, layerQuota);
            layers.remove(layerName);

            if (null == layerQuota.getUsedQuota()) {
                log.info("Calculating cache size for layer '" + layerName + "'");
                try {
                    int blockSize = quotaConfig.getDiskBlockSize();
                    double cacheSize = storageBroker.calculateCacheSize(layerName, blockSize);
                    layerQuota.setUsedQuota(cacheSize, MB);
                    double cacheGB = MB.convertTo(cacheSize, GB);
                    log.info("Cache size for " + layerName + " is " + cacheGB + "GB.");
                } catch (StorageException e) {
                    e.printStackTrace();
                }
            } else {
                log
                        .info("Used quota for layer '" + layerName + "' is "
                                + layerQuota.getUsedQuota());
            }
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

            int blockSize = quotaConfig.getDiskBlockSize();

            long actuallyUsedStorage = blockSize * (1 + blobSize / blockSize);

            LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
            Quota usedQuota = layerQuota.getUsedQuota();

            usedQuota.add(actuallyUsedStorage, B);

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

            int blockSize = quotaConfig.getDiskBlockSize();

            long actuallyUsedStorage = blockSize * (1 + blobSize / blockSize);

            LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
            Quota usedQuota = layerQuota.getUsedQuota();

            usedQuota.substract(actuallyUsedStorage, B);

            if (log.isDebugEnabled()) {
                log.debug("Used quota decreased for " + layerName + ": " + usedQuota);
            }
        }

        /**
         * @see org.geowebcache.storage.BlobStoreListener#layerDeleted(java.lang.String)
         */
        public void layerDeleted(final String layerName) {
            LayerQuotaExpirationPolicy layerQuotaPolicy = getPolicyFor(layerName);
            layerQuotaPolicy.dettach(layerName);
            quotaConfig.remove(quotaConfig.getLayerQuota(layerName));
        }

        private LayerQuotaExpirationPolicy getPolicyFor(final String layerName) {
            LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
            LayerQuotaExpirationPolicy expirationPolicy = layerQuota.getExpirationPolicy();
            return expirationPolicy;
        }
    }

    private static class CacheCleaner implements Runnable {

        private final DiskQuotaConfig quotaConfig;

        private final ExecutorService cleanUpExecutorService;

        private final Map<String, Future<?>> perLayerRunningCleanUps;

        private final ConfigLoader configLoader;

        public CacheCleaner(final ConfigLoader configLoader, final DiskQuotaConfig config,
                final ExecutorService executor) {

            this.configLoader = configLoader;
            this.quotaConfig = config;
            this.cleanUpExecutorService = executor;
            this.perLayerRunningCleanUps = new HashMap<String, Future<?>>();
        }

        public void run() {
            // first, save the config to account for changes in used quotas
            try {
                configLoader.saveConfig(quotaConfig);
            } catch (Exception e) {
                log.error("Error saving disk quota config", e);
            }
            
            for (LayerQuota lq : quotaConfig.getLayerQuotas()) {

                final String layerName = lq.getLayer();
                Future<?> runningCleanup = perLayerRunningCleanUps.get(layerName);
                if (runningCleanup != null && !runningCleanup.isDone()) {
                    log.info("Cache clean up task still running for layer '" + layerName
                            + "'. Ignoring it for this run.");
                    continue;
                }

                final Quota quota = lq.getQuota();
                final Quota usedQuota = lq.getUsedQuota();

                if (usedQuota != null) {
                    final double maxGigs = quota.getValue(StorageUnit.GB);
                    final double usedGigs = usedQuota.getValue(StorageUnit.GB);
                    if (usedGigs > maxGigs) {
                        log.info("Layer '" + lq.getLayer() + "' exceeds its quota. Quota: "
                                + maxGigs + "GB. Currently used: " + usedGigs
                                + "GB. Clean up task is gonna be performed"
                                + " using expiration policy " + lq.getExpirationPolicyName());

                        LayerQuotaEnforcementTask task;
                        task = new LayerQuotaEnforcementTask(lq);
                        Future<Object> future = this.cleanUpExecutorService.submit(task);
                        perLayerRunningCleanUps.put(layerName, future);
                    }
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
            final String layerName = layerQuota.getLayer();
            LayerQuotaExpirationPolicy expirationPolicy = layerQuota.getExpirationPolicy();
            expirationPolicy.expireTiles(layerName);

            return null;
        }
    }
}
