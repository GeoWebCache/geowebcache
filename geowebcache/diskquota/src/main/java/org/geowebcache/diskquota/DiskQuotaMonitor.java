package org.geowebcache.diskquota;

import static org.geowebcache.diskquota.StorageUnit.GB;
import static org.geowebcache.diskquota.StorageUnit.MB;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.springframework.beans.factory.DisposableBean;

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

    private final Map<String, LayerQuotaExpirationPolicy> layerPolicies;

    private final StorageBroker storageBroker;

    private DiskQuotaConfig quotaConfig;

    public DiskQuotaMonitor(ConfigLoader configLoader, TileLayerDispatcher tld, StorageBroker sb)
            throws IOException, ConfigurationException {

        this.storageBroker = sb;
        this.tileLayerDispatcher = tld;
        this.layerPolicies = new HashMap<String, LayerQuotaExpirationPolicy>();

        this.quotaConfig = configLoader.loadConfig();

        registerConfiguredLayers(configLoader);

        if (quotaConfig.getNumLayers() == 0 && quotaConfig.getDefaultQuota() == null) {
            log.info("No layer quotas defined nor default quota. Disk quota monitor is disabled.");
        } else {

            final MonitoringBlobListener blobListener = new MonitoringBlobListener(layerPolicies,
                    quotaConfig);

            storageBroker.addBlobStoreListener(blobListener);

            int totalLayers = tileLayerDispatcher.getLayers().size();
            int quotaLayers = quotaConfig.getNumLayers();
            log.info(quotaLayers + " layers configured with their own quotas. "
                    + (totalLayers - quotaLayers) + " subject to default quota: "
                    + quotaConfig.getDefaultQuota());
        }
    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
    }

    private void registerConfiguredLayers(final ConfigLoader configLoader) {

        Map<String, TileLayer> layers;
        layers = new HashMap<String, TileLayer>(tileLayerDispatcher.getLayers());
        List<LayerQuota> layerQuotas = quotaConfig.getLayerQuotas();

        for (LayerQuota layerQuota : layerQuotas) {

            final String layerName = layerQuota.getLayer();
            Quota quota = layerQuota.getQuota();
            final String policyName = quota.getExpirationPolicy();

            log.info("Attaching layer " + layerName + " to quota " + quota);

            final LayerQuotaExpirationPolicy expirationPolicy;
            expirationPolicy = configLoader.getExpirationPolicy(policyName);
            TileLayer tileLayer = layers.get(layerName);
            expirationPolicy.attach(tileLayer, layerQuota);
            layers.remove(layerName);
            layerPolicies.put(layerName, expirationPolicy);

            if (null == layerQuota.getUsedQuota()) {
                log.info("Calculating cache size for layer '" + layerName + "'");
                try {
                    double cacheSize = storageBroker.calculateCacheSize(layerName);
                    layerQuota.setUsedQuota(cacheSize, MB);
                    double cacheGB = MB.convertTo(cacheSize, GB);
                    log.info("Cache size for " + layerName + " is " + cacheGB + "GB.");
                } catch (StorageException e) {
                    e.printStackTrace();
                }
            }
        }

        // now set default quota to non explicitly configured layers
        Quota defaultQuota = quotaConfig.getDefaultQuota();
        if (defaultQuota != null) {
            log.info("Attaching remaining layers to DEFAULT quota " + defaultQuota);
            String policyName = defaultQuota.getExpirationPolicy();
            LayerQuotaExpirationPolicy expirationPolicy;
            expirationPolicy = configLoader.getExpirationPolicy(policyName);
            for (TileLayer layer : layers.values()) {
                String layerName = layer.getName();
                log.info("Attaching layer " + layerName + " to DEFAULT quota");
                LayerQuota lq = new LayerQuota();
                lq.setLayer(layerName);
                lq.setQuota(defaultQuota);

                this.quotaConfig.setLayerQuota(layerName, lq);

                expirationPolicy.attach(layer, lq);
                layerPolicies.put(layerName, expirationPolicy);
            }
        }
    }

    private static class MonitoringBlobListener implements BlobStoreListener {

        private final Map<String, LayerQuotaExpirationPolicy> layerPolicies;

        private final DiskQuotaConfig quotaConfig;

        public MonitoringBlobListener(final Map<String, LayerQuotaExpirationPolicy> layerPolicies,
                final DiskQuotaConfig quotaConfig) {
            this.layerPolicies = layerPolicies;
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

            long actuallyUsedStorage = 1 + blobSize / blockSize;

            LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
            Quota usedQuota = layerQuota.getUsedQuota();

            usedQuota.add(actuallyUsedStorage, StorageUnit.B);

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

            long actuallyUsedStorage = 1 + blobSize / blockSize;

            LayerQuota layerQuota = quotaConfig.getLayerQuota(layerName);
            Quota usedQuota = layerQuota.getUsedQuota();

            usedQuota.substract(actuallyUsedStorage, StorageUnit.B);

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

        private LayerQuotaExpirationPolicy getPolicyFor(String layerName) {
            return layerPolicies.get(layerName);
        }
    }

}
