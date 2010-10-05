package org.geowebcache.diskquota;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class CacheCleanerTask implements Runnable {

    static final Log log = LogFactory.getLog(CacheCleanerTask.class);

    private final DiskQuotaConfig quotaConfig;

    private final ExecutorService cleanUpExecutorService;

    /**
     * Maintains a set of per layer enforcement tasks, so that no enforcement task is spawn for a
     * layer when one is still running.
     */
    private final Map<String, Future<?>> perLayerRunningCleanUps;

    /**
     * Caches the currently running {@link GlobalQuotaEnforcementTask} so that not two are launched
     * at the same time
     */
    private Future<?> globalCleanUpTask;

    private final LayerCacheInfoBuilder cacheInfoBuilder;

    private final ConfigLoader configLoader;

    public CacheCleanerTask(final ConfigLoader configLoader, final DiskQuotaConfig config,
            final LayerCacheInfoBuilder cacheInfoBuilder, final ExecutorService executor) {

        this.configLoader = configLoader;
        this.quotaConfig = config;
        this.cleanUpExecutorService = executor;
        this.cacheInfoBuilder = cacheInfoBuilder;
        this.perLayerRunningCleanUps = new HashMap<String, Future<?>>();
    }

    public void run() {
        try {
            innerRun();
        } catch (Exception e) {
            log.error("Error running cache diskquota enforcement task", e);
        }
    }

    private void innerRun() {
        if (!quotaConfig.isEnabled()) {
            log.debug("DiskQuota disabled, ignoring run...");
            return;
        }
        // first, save the config to account for changes in used quotas
        if (quotaConfig.isDirty()) {
            try {
                configLoader.saveConfig(quotaConfig);
                quotaConfig.setDirty(false);
            } catch (Exception e) {
                log.error("Error saving disk quota config", e);
            }
        }

        final List<LayerQuota> globallyManagedQuotas = new ArrayList<LayerQuota>();
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

            // may be null, meaning there's no defined quota for the layer, but there's a global
            // quota and hence the layer's usage must be treated with all the globally managed ones
            final Quota quota = lq.getQuota();
            if (quota == null) {
                if (quotaConfig.getGlobalQuota() != null) {
                    if (lq.isDirty()) {
                        quotaConfig.getGlobalExpirationPolicy().save(layerName);
                        lq.setDirty(false);
                    }
                    globallyManagedQuotas.add(lq);
                }
                continue;
            }
            // never null
            final Quota usedQuota = lq.getUsedQuota();
            ExpirationPolicy expirationPolicy = lq.getExpirationPolicy();
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
        if (globallyManagedQuotas.size() > 0) {
            if (globalCleanUpTask != null && !globalCleanUpTask.isDone()) {
                log.info("Global cache quota enforcement task still running, avoiding issueing a new one...");
                return;
            }
            Quota globalQuota = quotaConfig.getGlobalQuota();
            Quota globalUsedQuota = quotaConfig.getGlobalUsedQuota();
            Quota excedent = globalUsedQuota.difference(globalQuota);
            if (excedent.getValue().compareTo(BigDecimal.ZERO) > 0) {
                log.info("Submitting global cache quota enforcement task");
                GlobalQuotaEnforcementTask task;
                task = new GlobalQuotaEnforcementTask(quotaConfig, globallyManagedQuotas);
                this.globalCleanUpTask = this.cleanUpExecutorService.submit(task);
            } else {
                log.info("Global quota not reached, there are still " + excedent.toNiceString()
                        + " to go.");
            }
        }
    }

    /**
     * 
     * @author Gabriel Roldan
     */
    private static class LayerQuotaEnforcementTask implements Callable<Object> {

        private final LayerQuota layerQuota;

        public LayerQuotaEnforcementTask(final LayerQuota layerQuota) {
            this.layerQuota = layerQuota;
        }

        public Object call() throws Exception {
            try {
                final String layerName = layerQuota.getLayer();
                ExpirationPolicy expirationPolicy = layerQuota.getExpirationPolicy();
                expirationPolicy.expireTiles(layerName);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
            return null;
        }
    }

    /**
     * Tries to expire tiles from all the globally managed layer quotas in a proportional way to the
     * current layer's disk usage in relation to the global cache usage.
     * <p>
     * Proportionally meaning that
     * </p>
     * 
     * @author Gabriel Roldan
     * @see ExpirationPolicy#expireTiles(List, Quota, Quota)
     */
    private static class GlobalQuotaEnforcementTask implements Callable<Object> {

        private final DiskQuotaConfig quotaConfig;

        private final List<LayerQuota> globallyManagedQuotas;

        public GlobalQuotaEnforcementTask(final DiskQuotaConfig quotaConfig,
                final List<LayerQuota> globallyManagedQuotas) {
            this.quotaConfig = quotaConfig;
            this.globallyManagedQuotas = globallyManagedQuotas;
        }

        /**
         * Calls the global expiration policy's
         * {@link ExpirationPolicy#expireTiles(List, Quota, Quota) expireTiles(List, Quota, Quota)}
         * method with the list of globally managed layer names, the global quota limit and the
         * current global quota usage.
         * 
         * @see java.util.concurrent.Callable#call()
         */
        public Object call() throws Exception {
            ExpirationPolicy globalExpirationPolicy = quotaConfig.getGlobalExpirationPolicy();
            List<String> layerNames = new ArrayList<String>();
            for (LayerQuota lq : globallyManagedQuotas) {
                layerNames.add(lq.getLayer());
            }
            Quota globalLimit = quotaConfig.getGlobalQuota();
            Quota globalUsage = quotaConfig.getGlobalUsedQuota();
            globalExpirationPolicy.expireTiles(layerNames, globalLimit, globalUsage);
            return null;
        }
    }

}