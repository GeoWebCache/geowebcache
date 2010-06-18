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
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageBrokerListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
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

    private final Map<String, LayerQuotaExpirationPolicy> enabledPolicies;

    private final StorageBroker storageBroker;

    private DiskQuotaConfig quotaConfig;

    public DiskQuotaMonitor(ConfigLoader configLoader, TileLayerDispatcher tld, StorageBroker sb)
            throws IOException, ConfigurationException {

        this.storageBroker = sb;
        this.tileLayerDispatcher = tld;
        this.enabledPolicies = new HashMap<String, LayerQuotaExpirationPolicy>();

        this.quotaConfig = configLoader.loadConfig();

        registerConfiguredLayers(configLoader);

        if (quotaConfig.getNumLayers() == 0 && quotaConfig.getDefaultQuota() == null) {
            log.info("No layer quotas defined nor default quota. Disk quota monitor is disabled.");
        } else {
            int totalLayers = tileLayerDispatcher.getLayers().size();
            int quotaLayers = quotaConfig.getNumLayers();
            log.info(quotaLayers + " layers configured with their own quotas. "
                    + (totalLayers - quotaLayers) + " subject to default quota: "
                    + quotaConfig.getDefaultQuota());
        }
        storageBroker.addStorageBrokerListener(new StorageBrokerListener() {

            /**
             * @see org.geowebcache.storage.StorageBrokerListener#tileRangeExpired(org.geowebcache.storage.TileRange)
             */
            public void tileRangeExpired(TileRange tileRange) {

            }

            /**
             * @see org.geowebcache.storage.StorageBrokerListener#tileRangeDeleted(org.geowebcache.storage.TileRange)
             */
            public void tileRangeDeleted(TileRange tileRange) {
            }

            /**
             * @see org.geowebcache.storage.StorageBrokerListener#tileCached(org.geowebcache.storage.TileObject)
             */
            public void tileCached(TileObject tileObj) {
            }

            /**
             * @see org.geowebcache.storage.StorageBrokerListener#shutDown()
             */
            public void shutDown() {
            }

            /**
             * @see org.geowebcache.storage.StorageBrokerListener#layerDeleted(java.lang.String)
             */
            public void layerDeleted(String layerName) {
            }

            /**
             * @see org.geowebcache.storage.StorageBrokerListener#cacheMiss(org.geowebcache.storage.TileObject)
             */
            public void cacheMiss(TileObject tileObj) {
            }

            /**
             * @see org.geowebcache.storage.StorageBrokerListener#cacheHit(org.geowebcache.storage.TileObject)
             */
            public void cacheHit(TileObject tileObj) {
            }
        });
    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
    }

    private void registerConfiguredLayers(ConfigLoader configLoader) {

        Map<String, TileLayer> layers;
        layers = new HashMap<String, TileLayer>(tileLayerDispatcher.getLayers());
        List<LayerQuota> layerQuotas = quotaConfig.getLayerQuotas();

        LayerQuotaExpirationPolicy expirationPolicy;

        for (LayerQuota lq : layerQuotas) {
            String layerName = lq.getLayer();
            Quota quota = lq.getQuota();
            String policyName = quota.getExpirationPolicy();
            log.info("Attaching layer " + layerName + " to quota " + quota);
            expirationPolicy = configLoader.getExpirationPolicy(policyName);
            TileLayer tileLayer = layers.get(layerName);
            expirationPolicy.attach(tileLayer, quota);
            layers.remove(layerName);

//            log.info("Calculating cache size for layer " + layerName);
//            try {
//                double cacheSize = storageBroker.calculateCacheSize(layerName);
//                double cacheGB = MB.convertTo(cacheSize, GB);
//                log.info("Cache size for " + layerName + " is " + cacheGB + "GB.");
//            } catch (StorageException e) {
//                e.printStackTrace();
//            }
        }

        // now set default quota to non explicitly configured layers
        Quota defaultQuota = quotaConfig.getDefaultQuota();
        if (defaultQuota != null) {
            log.info("Attaching remaining layers to DEFAULT quota " + defaultQuota);
            String policyName = defaultQuota.getExpirationPolicy();
            expirationPolicy = configLoader.getExpirationPolicy(policyName);
            for (TileLayer layer : layers.values()) {
                String layerName = layer.getName();
                log.info("Attaching layer " + layerName + " to DEFAULT quota");
                expirationPolicy.attach(layer, defaultQuota);
            }
        }
    }
}
