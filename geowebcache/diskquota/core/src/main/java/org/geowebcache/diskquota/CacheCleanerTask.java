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

import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.diskquota.CacheCleaner.QuotaResolver;
import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;

class CacheCleanerTask implements Runnable {

    static final Log log = LogFactory.getLog(CacheCleanerTask.class);

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

    private ExecutorService cleanUpExecutorService;

    private final DiskQuotaMonitor monitor;

    /**
     * @param executor
     *            ExecutorService used to launch quota enforcement tasks
     */
    public CacheCleanerTask(final DiskQuotaMonitor monitor, final ExecutorService executor) {
        this.monitor = monitor;
        this.cleanUpExecutorService = executor;
        this.perLayerRunningCleanUps = new HashMap<String, Future<?>>();
    }

    /**
     * Runs the cache enforcement tasks asynchronously using the {@link ExecutorService} provided in
     * the constructor.
     * <p>
     * Exceptions are catched and logged, not propagated, in order to allow this runnable to be used
     * as a timer so even if one run fails the next runs are still called
     * </p>
     * <p>
     * The process submits one cache cleanup execution task per layer that exceeds it's configured
     * quota, and a single global cache enforcement task for the layers that have no explicitly
     * configured quota limit.
     * </p>
     * 
     * @see java.lang.Runnable#run()
     */
    public void run() {
        try {
            innerRun();
        } catch (InterruptedException e) {
            log.info("CacheCleanerTask called for shut down", e);
        } catch (Exception e) {
            log.error("Error running cache diskquota enforcement task", e);
        }
    }

    private void innerRun() throws InterruptedException {
        // first, save the config to account for changes in used quotas

        final DiskQuotaConfig quotaConfig = monitor.getConfig();
        if (!quotaConfig.isEnabled()) {
            log.trace("DiskQuota disabled, ignoring run...");
            return;
        }

        quotaConfig.setLastCleanUpTime(new Date());

        final Set<String> allLayerNames = monitor.getLayerNames();
        final Set<String> configuredLayerNames = quotaConfig.layerNames();
        final Set<String> globallyManagedLayerNames = new HashSet<String>(allLayerNames);

        globallyManagedLayerNames.removeAll(configuredLayerNames);

        for (String layerName : configuredLayerNames) {

            if (monitor.isCacheInfoBuilderRunning(layerName)) {
                if (log.isInfoEnabled()) {
                    log.info("Cache information is still being gathered for layer '" + layerName
                            + "'. Skipping quota enforcement task for this layer.");
                }
                continue;
            }

            Future<?> runningCleanup = perLayerRunningCleanUps.get(layerName);
            if (runningCleanup != null && !runningCleanup.isDone()) {
                if (log.isDebugEnabled()) {
                    log.debug("Cache clean up task still running for layer '" + layerName
                            + "'. Ignoring it for this run.");
                }
                continue;
            }

            final LayerQuota definedQuotaForLayer = quotaConfig.layerQuota(layerName);
            final ExpirationPolicy policy = definedQuotaForLayer.getExpirationPolicyName();
            final Quota quota = definedQuotaForLayer.getQuota();
            final Quota usedQuota = monitor.getUsedQuotaByLayerName(layerName);

            Quota excedent = usedQuota.difference(quota);
            if (excedent.getBytes().compareTo(BigInteger.ZERO) > 0) {
                if (log.isInfoEnabled()) {
                    log.info("Layer '" + layerName + "' exceeds its quota of "
                            + quota.toNiceString() + " by " + excedent.toNiceString()
                            + ". Currently used: " + usedQuota.toNiceString()
                            + ". Clean up task will be performed using expiration policy " + policy);
                }

                Set<String> layerNames = Collections.singleton(layerName);
                QuotaResolver quotaResolver;
                quotaResolver = monitor.newLayerQuotaResolver(layerName);

                LayerQuotaEnforcementTask task;
                task = new LayerQuotaEnforcementTask(layerNames, quotaResolver, monitor);
                Future<Object> future = this.cleanUpExecutorService.submit(task);
                perLayerRunningCleanUps.put(layerName, future);
            }
        }

        if (globallyManagedLayerNames.size() > 0) {
            ExpirationPolicy globalExpirationPolicy = quotaConfig.getGlobalExpirationPolicyName();
            if (globalExpirationPolicy == null) {
                return;
            }
            final Quota globalQuota = quotaConfig.getGlobalQuota();
            if (globalQuota == null) {
                log.info("There's not a global disk quota configured. The following layers "
                        + "will not be checked for excess of disk usage: "
                        + globallyManagedLayerNames);
                return;
            }

            if (globalCleanUpTask != null && !globalCleanUpTask.isDone()) {
                log.debug("Global cache quota enforcement task still running, avoiding issueing a new one...");
                return;
            }

            Quota globalUsedQuota = monitor.getGloballyUsedQuota();
            Quota excedent = globalUsedQuota.difference(globalQuota);

            if (excedent.getBytes().compareTo(BigInteger.ZERO) > 0) {

                log.debug("Submitting global cache quota enforcement task");
                LayerQuotaEnforcementTask task;
                QuotaResolver quotaResolver = monitor.newGlobalQuotaResolver();
                task = new LayerQuotaEnforcementTask(globallyManagedLayerNames, quotaResolver,
                        monitor);
                this.globalCleanUpTask = this.cleanUpExecutorService.submit(task);
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("Won't launch global quota enforcement task, "
                            + globalUsedQuota.toNiceString() + " used out of "
                            + globalQuota.toNiceString() + " configured for the whole cache size.");
                }
            }
        }
    }

    /**
     * 
     * @author Gabriel Roldan
     */
    private static class LayerQuotaEnforcementTask implements Callable<Object> {

        private final Set<String> layerNames;

        private final QuotaResolver quotaResolver;

        private final DiskQuotaMonitor monitor;

        public LayerQuotaEnforcementTask(final Set<String> layerNames,
                final QuotaResolver quotaResolver, final DiskQuotaMonitor monitor) {
            this.layerNames = layerNames;
            this.quotaResolver = quotaResolver;
            this.monitor = monitor;
        }

        /**
         * @see java.util.concurrent.Callable#call()
         */
        public Object call() throws Exception {
            try {
                monitor.expireByLayerNames(layerNames, quotaResolver);
            } catch (InterruptedException e) {
                log.info("Layer quota enforcement task terminated prematurely");
                return null;
            } catch (Exception e) {
                log.warn("Exception expiring tiles for " + layerNames, e);
                throw e;
            }
            return null;
        }

    }

}