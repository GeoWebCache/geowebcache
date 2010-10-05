package org.geowebcache.diskquota;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

class CacheCleanerTask implements Runnable {

    private final DiskQuotaConfig quotaConfig;

    private final ExecutorService cleanUpExecutorService;

    private final Map<String, Future<?>> perLayerRunningCleanUps;

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
        // first, save the config to account for changes in used quotas
        if (quotaConfig.isDirty()) {
            try {
                configLoader.saveConfig(quotaConfig);
                quotaConfig.setDirty(false);
            } catch (Exception e) {
                DiskQuotaMonitor.log.error("Error saving disk quota config", e);
            }
        }

        for (LayerQuota lq : quotaConfig.getLayerQuotas()) {
            final String layerName = lq.getLayer();

            if (cacheInfoBuilder.isRunning(layerName)) {
                DiskQuotaMonitor.log.info("Cache information is still being gathered for layer '"
                        + layerName + "'. Skipping quota enforcement task for this layer.");
                continue;
            }

            Future<?> runningCleanup = perLayerRunningCleanUps.get(layerName);
            if (runningCleanup != null && !runningCleanup.isDone()) {
                DiskQuotaMonitor.log.debug("Cache clean up task still running for layer '"
                        + layerName + "'. Ignoring it for this run.");
                continue;
            }

            final Quota quota = lq.getQuota();
            final Quota usedQuota = lq.getUsedQuota();
            ExpirationPolicy expirationPolicy = lq.getExpirationPolicy();
            if (lq.isDirty()) {
                expirationPolicy.save(layerName);
                lq.setDirty(false);
            }

            Quota excedent = usedQuota.difference(quota);
            if (excedent.getValue().compareTo(BigDecimal.ZERO) > 0) {
                DiskQuotaMonitor.log.info("Layer '" + lq.getLayer() + "' exceeds its quota of "
                        + quota.toNiceString() + " by " + excedent.toNiceString()
                        + ". Currently used: " + usedQuota.toNiceString()
                        + ". Clean up task is gonna be performed" + " using expiration policy "
                        + lq.getExpirationPolicyName());

                CacheCleanerTask.LayerQuotaEnforcementTask task;
                task = new LayerQuotaEnforcementTask(lq);
                Future<Object> future = this.cleanUpExecutorService.submit(task);
                perLayerRunningCleanUps.put(layerName, future);
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
}