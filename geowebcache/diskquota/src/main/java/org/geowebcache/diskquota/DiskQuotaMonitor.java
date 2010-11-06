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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * Monitors the layers cache size given each one's assigned {@link Quota} and call's the exceeded
 * layer's {@link ExpirationPolicy expiration policy} for cache clean up.
 * <p>
 * This monitor only cares about checking layers do not exceed their configured cache disk quota.
 * </p>
 * <p>
 * When a layer exceeds its quota, the {@link ExpirationPolicy} it is attached to is called to
 * {@link ExpirationPolicy#expireTiles(String) whip out} storage space.
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

        this.cleanUpExecutorService = createCleanUpExecutor();

        attachConfiguredLayers();

        this.cacheInfoBuilder = launchCacheInfoGatheringThreads();

        final MonitoringBlobListener blobListener = new MonitoringBlobListener(quotaConfig);
        storageBroker.addBlobStoreListener(blobListener);

        setUpScheduledCleanUp();
    }

    /**
     * Called when the framework destroys this bean (e.g. due to web app shutdown), stops any
     * running scheduled clean up and gracefuly shuts down
     * 
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        if (this.cleanUpExecutorService != null) {
            this.cleanUpExecutorService.shutdownNow();
        }
        if (this.cacheInfoBuilder != null) {
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

        Runnable command = new CacheCleanerTask(configLoader, quotaConfig, cacheInfoBuilder,
                cleanUpExecutorService);

        long initialDelay = quotaConfig.getCacheCleanUpFrequency();
        long period = quotaConfig.getCacheCleanUpFrequency();
        TimeUnit unit = quotaConfig.getCacheCleanUpUnits();
        cleanUpExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
        log.info("Disk quota periodic enforcement task set up every " + period + " " + unit);
    }

    /**
     * Attaches each {@link LayerQuota} to its {@link ExpirationPolicy} by first looking for the
     * layer's declared expiration policy implementation and then calling
     * {@link ExpirationPolicy#attach(TileLayer, LayerQuota)}
     */
    private void attachConfiguredLayers() {

        final Map<String, TileLayer> layers = new HashMap<String, TileLayer>(
                tileLayerDispatcher.getLayers());
        
        final List<LayerQuota> layerQuotas = quotaConfig.getLayerQuotas();
        
        final ExpirationPolicy globalExpirationPolicy = quotaConfig.getGlobalExpirationPolicy();
        final Quota globalQuota = quotaConfig.getGlobalQuota();

        int explicitConfigs = 0;
        int globallyConfigured = 0;

        for (LayerQuota layerQuota : layerQuotas) {
            final String layerName = layerQuota.getLayer();
            final TileLayer tileLayer = layers.get(layerName);
            final String policyName = layerQuota.getExpirationPolicyName();
            if (policyName == null) {
                if (globalExpirationPolicy != null) {
                    // not a configured layer. The global expiration policy will take care of it
                    globallyConfigured++;
                    log.trace("Attaching layer " + layerName + " to global quota " + globalQuota);
                    layerQuota.setExpirationPolicy(globalExpirationPolicy);
                    globalExpirationPolicy.attach(tileLayer, layerQuota);
                }
            } else {
                final Quota quota = layerQuota.getQuota();
                final ExpirationPolicy expirationPolicy = configLoader
                        .findExpirationPolicy(policyName);
                explicitConfigs++;
                layerQuota.setExpirationPolicy(expirationPolicy);
                expirationPolicy.attach(tileLayer, layerQuota);
                log.trace("Attaching layer " + layerName + " to quota " + quota);
            }
        }
        log.info(explicitConfigs + " layers configured with their own quotas. "
                + globallyConfigured + " layers attached to global quota " + globalQuota);

    }
}
