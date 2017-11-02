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
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.diskquota.CacheCleaner.GlobalQuotaResolver;
import org.geowebcache.diskquota.CacheCleaner.LayerQuotaResolver;
import org.geowebcache.diskquota.CacheCleaner.QuotaResolver;
import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageBroker;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.Assert;

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
public class DiskQuotaMonitor implements InitializingBean, DisposableBean {

    private static final Log log = LogFactory.getLog(DiskQuotaMonitor.class);

    /**
     * Name of the environment variable that if present disables completely the DiskQuotaMonitor,
     * meaning that no tile access nor disk usage statistics will be gathered during the life time
     * of the Java VM.
     */
    public static final String GWC_DISKQUOTA_DISABLED = "GWC_DISKQUOTA_DISABLED";

    private final TileLayerDispatcher tileLayerDispatcher;

    private final StorageBroker storageBroker;

    private final CacheCleaner cacheCleaner;

    /**
     * Loads and saves quota limits and quota usage status for configured layers
     */
    private final ConfigLoader configLoader;

    /**
     * Disk quota config object loaded and saved by {@link #configLoader}
     */
    private DiskQuotaConfig quotaConfig;

    private LayerCacheInfoBuilder cacheInfoBuilder;

    private QuotaStore quotaStore;

    /**
     * Executor service for the periodic clean up of layers caches that exceed its quota
     * 
     * @see #setUpScheduledCleanUp()
     * @see #destroy()
     */
    private ScheduledExecutorService cleanUpExecutorService;

    private QuotaUpdatesMonitor quotaUsageMonitor;

    private UsageStatsMonitor usageStatsMonitor;

    private volatile boolean isRunning;

    private final DefaultStorageFinder storageFinder;

    private boolean diskQuotaEnabled;

    private QuotaStoreProvider quotaStoreProvider;

    /**
     * 
     * @param configLoader
     *            loads and saves the layers quota config and usage status
     * @param tld
     *            provides access to the layers configured for disk quota insurance
     *            quota usage
     * @throws IOException
     * @throws ConfigurationException
     */
    public DiskQuotaMonitor(final DefaultStorageFinder storageFinder,
            final ConfigLoader configLoader, final TileLayerDispatcher tld, final StorageBroker sb,
            QuotaStoreProvider quotaStoreProvider, final CacheCleaner cacheCleaner) throws IOException,
            ConfigurationException {

        boolean disabled = Boolean.valueOf(storageFinder.findEnvVar(GWC_DISKQUOTA_DISABLED))
                .booleanValue();
        if (disabled) {
            log.warn(" -- Found environment variable " + GWC_DISKQUOTA_DISABLED
                    + " set to true. DiskQuotaMonitor is disabled.");
        }
        this.diskQuotaEnabled = !disabled;
        
        this.storageFinder = storageFinder;
        this.configLoader = configLoader;
        this.storageBroker = sb;
        this.tileLayerDispatcher = tld;
        this.quotaStoreProvider = quotaStoreProvider;
        this.cacheCleaner = cacheCleaner;
    }
    
    /**
     * Returns the quota store provider used by this class (used in {@link #startUp()}) to
     * get the {@link QuotaStore} instance.
     */
    public QuotaStoreProvider getQuotaStoreProvider() {
        return quotaStoreProvider;
    }
    
    /**
     * Returns the quota store monitored by this class
     * @return
     */
    public QuotaStore getQuotaStore() {
        return quotaStore;
    }

    /**
     * Returns whether the DiskQuotaMonitor is enabled.
     * <p>
     * It is always enabled at least the {@link #GWC_DISKQUOTA_DISABLED} environment variable has
     * been set to {@code true}
     * </p>
     */
    public boolean isEnabled() {
        return diskQuotaEnabled;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Called when the framework calls this bean for initialization
     * <p>
     * Defers to {@link #startUp()}, or does nothing if {@code isEnabled() == false}
     * </p>
     * 
     * @see #isEnabled()
     * @see #startUp()
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception {
        if (!diskQuotaEnabled) {
            return;
        }
        startUp();
    }

    /**
     * Called when the framework destroys this bean (e.g. due to web app shutdown), stops any
     * running scheduled clean up and gracefuly shuts down.
     * <p>
     * This method does nothing if {@code #isEnabled() == false}
     * </p>
     * 
     * @see #shutDown()
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        if (!diskQuotaEnabled) {
            return;
        }
        shutDown(30);
    }

    /**
     * Starts up the tile usage and disk usage monitors
     * <p>
     * <b>Preconditions:</b>
     * <ul>
     * <li> {@link #isEnabled() == true}
     * <li> {@link #isRunning() == false}
     * </ul>
     * </p>
     * <b>Postconditions:</b>
     * <ul>
     * <li> {@link #isRunning() == true}
     * </ul>
     * </p>
     * 
     * @throws ConfigurationException
     * @see {@link #shutDown(int)}
     */
    public void startUp() throws ConfigurationException, IOException {
        Assert.isTrue(diskQuotaEnabled, "startUp called but DiskQuotaMonitor is disabled!");
        Assert.isTrue(!isRunning, "DiskQuotaMonitor is already running");

        try {
            startUpInternal();
            isRunning = true;
        } catch (InterruptedException e) {
            log.info("DiskQuotaMonitor startup process interrupted", e);
        }
    }

    private void startUpInternal() throws InterruptedException, ConfigurationException, IOException {
        try {
            this.quotaConfig = configLoader.loadConfig();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        quotaStore = quotaStoreProvider.getQuotaStore();

        quotaUsageMonitor = new QuotaUpdatesMonitor(quotaConfig, storageBroker, quotaStore);
        usageStatsMonitor = new UsageStatsMonitor(quotaStore, tileLayerDispatcher);

        if (cleanUpExecutorService != null) {
            log.info("Shutting down clean up executor service...");
            cleanUpExecutorService.shutdownNow();
        }

        this.cleanUpExecutorService = createCleanUpExecutor();

        attachConfiguredLayers();

        quotaUsageMonitor.startUp();

        usageStatsMonitor.startUp();

        // the tasks that poll the status of the used quotas periodically and performs the clean up
        // when a quota is exceeded
        setUpScheduledCleanUp();

        // the startup might be called more than once (happens in GeoServer after disk quota
        // re-configuration for example), in this case shut down the old cache info builder
        if(this.cacheInfoBuilder != null) {
            cacheInfoBuilder.shutDown();
        }
        this.cacheInfoBuilder = launchCacheInfoGatheringThreads();
    }

    /**
     * Shuts down the tile and disk space usage monitors.
     * <p>
     * <b>Preconditions</b>:
     * <ul>
     * <li> {@link #isEnabled() == true}
     * <li> {@code timeOutSecs > 0}
     * </ul>
     * </p>
     * 
     * @param timeOutSecs
     *            time out in seconds to wait for the related services to shut down, must be a
     *            positive integer > 0
     * @throws InterruptedException
     *             if some service failed to gracefully shut down within a reasonable amount of
     *             time, upon which it is safe to call this method again for a retry
     * @see #startUp()
     */
    public void shutDown(final int timeOutSecs) throws InterruptedException {
        Assert.isTrue(diskQuotaEnabled, "shutDown called but DiskQuotaMonitor is disabled!");
        Assert.isTrue(timeOutSecs > 0, "timeOut for shutdown must be > 0: " + timeOutSecs);
        try {
            log.info("Disk quota monitor shutting down...");
            if (this.cacheInfoBuilder != null) {
                this.cacheInfoBuilder.shutDown();
            }

            if (this.cleanUpExecutorService != null) {
                this.cleanUpExecutorService.shutdownNow();
            }

            log.info("Shutting down quota usage monitor...");
            quotaUsageMonitor.shutDownNow();

            log.info("Shutting down quota statistics gathering monitor...");
            usageStatsMonitor.shutDownNow();

            quotaUsageMonitor.awaitTermination(timeOutSecs * 1000, TimeUnit.MILLISECONDS);

            usageStatsMonitor.awaitTermination(timeOutSecs * 1000, TimeUnit.MILLISECONDS);
        } finally {
            isRunning = false;
        }
    }

    /**
     * <p>
     * <b>Preconditions:</b>
     * <ul>
     * <li> {@link #isEnabled() == true}
     * </ul>
     * </p>
     * 
     * @return the current configuration
     */
    public DiskQuotaConfig getConfig() {
        Assert.isTrue(diskQuotaEnabled, "called saveConfig but DiskQuota is disabled!");
        return this.quotaConfig;
    }

    /**
     * Saves and set the given configuration.
     * <p>
     * <b>Preconditions:</b>
     * <ul>
     * <li> {@link #isEnabled() == true}
     * </ul>
     * </p>
     * 
     * @param config
     */
    public void saveConfig(DiskQuotaConfig config) {
        Assert.isTrue(diskQuotaEnabled, "called saveConfig but DiskQuota is disabled!");
        try {
            configLoader.saveConfig(config);
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (config != quotaConfig) {
            this.quotaConfig.setFrom(config);
        }
    }

    /**
     * Reloads the configuration from disk
     * @param config
     * @throws IOException 
     * @throws ConfigurationException 
     */
    public void reloadConfig() throws ConfigurationException, IOException {
        DiskQuotaConfig config = configLoader.loadConfig();
        this.quotaConfig.setFrom(config);
    }

    /**
     * @see #saveConfig(DiskQuotaConfig)
     */
    public void saveConfig() {
        saveConfig(quotaConfig);
    }

    /**
     * Launches a background task to traverse the cache and compute the disk usage of each layer
     * that has no {@link LayerQuota#getUsedQuota() used quota} already loaded.
     * 
     * @return
     * @throws InterruptedException
     */
    private LayerCacheInfoBuilder launchCacheInfoGatheringThreads() throws InterruptedException {

        LayerCacheInfoBuilder cacheInfoBuilder;
        File cacheRoot;
        try {
            cacheRoot = new File(storageFinder.getDefaultPath());
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
        cacheInfoBuilder = new LayerCacheInfoBuilder(cacheRoot, cleanUpExecutorService,
                quotaUsageMonitor);

        for (String layerName : tileLayerDispatcher.getLayerNames()) {

            Quota usedQuota = quotaStore.getUsedQuotaByLayerName(layerName);
            if (usedQuota.getBytes().compareTo(BigInteger.ZERO) > 0) {
                log.debug("Using saved quota information for layer " + layerName + ": "
                        + usedQuota.toNiceString());
            } else {
                log.debug(layerName + " has no saved used quota information,"
                        + "traversing layer cache to compute its disk usage.");
                TileLayer tileLayer;
                try {
                    tileLayer = tileLayerDispatcher.getTileLayer(layerName);
                } catch (GeoWebCacheException e) {
                    log.debug(e);
                    continue;
                }
                cacheInfoBuilder.buildCacheInfo(tileLayer);
            }
        }
        return cacheInfoBuilder;
    }

    private ScheduledExecutorService createCleanUpExecutor() {

        final int numCleaningThreads = quotaConfig.getMaxConcurrentCleanUps();
        log.info("Setting up disk quota periodic enforcement task");
        CustomizableThreadFactory tf = new CustomizableThreadFactory(
                "GWC DiskQuota clean up thread-");
        tf.setThreadPriority(1 + (Thread.MAX_PRIORITY - Thread.MIN_PRIORITY) / 5);

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(
                numCleaningThreads, tf);

        return executorService;
    }

    private void setUpScheduledCleanUp() {

        Runnable scheduledCleaningTask = new CacheCleanerTask(this, cleanUpExecutorService);

        long delay = quotaConfig.getCacheCleanUpFrequency();
        long period = quotaConfig.getCacheCleanUpFrequency();
        TimeUnit unit = quotaConfig.getCacheCleanUpUnits();
        cleanUpExecutorService.scheduleAtFixedRate(scheduledCleaningTask, delay, period, unit);

        log.info("Disk quota periodic enforcement task set up every " + period + " " + unit);
    }

    /**
     * Sets the {@link LayerQuota#setExpirationPolicy(ExpirationPolicy) expiration policy} to all
     * the configured layer quotas based on their {@link LayerQuota#getExpirationPolicyName()
     * declared expiration policy name}
     * 
     * @throws ConfigurationException
     */
    private void attachConfiguredLayers() throws ConfigurationException {

        final List<LayerQuota> layerQuotas = quotaConfig.getLayerQuotas();

        final ExpirationPolicy globalExpirationPolicy = quotaConfig.getGlobalExpirationPolicyName();
        final Quota globalQuota = quotaConfig.getGlobalQuota();

        int explicitConfigs = 0;

        if (layerQuotas != null) {
            for (LayerQuota layerQuota : layerQuotas) {
                final String layerName = layerQuota.getLayer();
                final ExpirationPolicy policyName = layerQuota.getExpirationPolicyName();
                if (policyName != null) {
                    final Quota quota = layerQuota.getQuota();
                    explicitConfigs++;
                    log.trace("Attaching layer " + layerName + " to quota " + quota
                            + " with expiration policy " + policyName);
                }
            }
        }
        log.info(explicitConfigs + " layers configured with their own quotas. ");
        if (globalExpirationPolicy != null) {
            int globallyConfigured = tileLayerDispatcher.getLayerCount() - explicitConfigs;
            log.info(globallyConfigured + " layers attached to global quota "
                    + globalQuota.toNiceString());
        }

    }

    public QuotaResolver newLayerQuotaResolver(final String layerName) {
        LayerQuota layerQuota = quotaConfig.layerQuota(layerName);
        return new LayerQuotaResolver(layerQuota, quotaStore);
    }

    public QuotaResolver newGlobalQuotaResolver() {
        return new GlobalQuotaResolver(quotaConfig, quotaStore);
    }

    /**
     * @see TileLayerDispatcher#getLayerNames()
     */
    public Set<String> getLayerNames() {
        return tileLayerDispatcher.getLayerNames();
    }

    /**
     * @see LayerCacheInfoBuilder#isRunning(String)
     */
    public boolean isCacheInfoBuilderRunning(String layerName) {
        return cacheInfoBuilder != null && cacheInfoBuilder.isRunning(layerName);
    }

    /**
     * @see QuotaStore#getUsedQuotaByLayerName(String)
     */
    public Quota getUsedQuotaByLayerName(String layerName) throws InterruptedException {
        return quotaStore.getUsedQuotaByLayerName(layerName);
    }

    /**
     * @see QuotaStore#getGloballyUsedQuota()
     */
    public Quota getGloballyUsedQuota() throws InterruptedException {
        return quotaStore.getGloballyUsedQuota();
    }

    /**
     * <p>
     * <b>Preconditions</b>:
     * <ul>
     * <li> {@link #isRunning() == true}
     * </ul>
     * </p>
     * 
     * @see CacheCleaner#expireByLayerNames(Set, QuotaResolver)
     */
    public void expireByLayerNames(Set<String> layerNames, QuotaResolver quotaResolver)
            throws InterruptedException {
        cacheCleaner.expireByLayerNames(layerNames, quotaResolver, quotaStore);
    }
}
