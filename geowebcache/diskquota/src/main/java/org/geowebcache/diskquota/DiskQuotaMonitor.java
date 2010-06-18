package org.geowebcache.diskquota;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.context.WebApplicationContext;

public class DiskQuotaMonitor implements InitializingBean, DisposableBean {

    private static final Log log = LogFactory.getLog(DiskQuotaMonitor.class);

    private static final String CONFIGURATION_FILE_NAME = "geowebcache-diskquota.xml";

    private static final String[] CONFIGURATION_REL_PATHS = { "/WEB-INF/classes", "/../resources" };

    private WebApplicationContext context;

    private URL configFile;

    private DiskQuotaConfig quotaConfig;

    private TileLayerDispatcher tileLayerDispatcher;

    private DefaultStorageFinder storageFinder;

    public DiskQuotaMonitor(DefaultStorageFinder storageFinder,
            ApplicationContextProvider contextProvider, TileLayerDispatcher tld) throws IOException {

        this.storageFinder = storageFinder;
        this.context = contextProvider.getApplicationContext();
        this.tileLayerDispatcher = tld;
    }

    /**
     * Looks up for, parses and validates the layer quota configuration.
     * 
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception {
        String cachePath;
        try {
            cachePath = storageFinder.getDefaultPath();
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
        try {
            this.configFile = findConfFile(cachePath);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }

        if (configFile == null) {
            log.info("Found no " + CONFIGURATION_FILE_NAME + " file. Disk Quota is disabled.");
            return;
        }

        InputStream configIn = configFile.openStream();
        try {
            this.quotaConfig = ConfigLoader.loadConfiguration(configIn);
        } finally {
            configIn.close();
        }

        validateConfig();

        if (quotaConfig.getNumLayers() == 0 && quotaConfig.getDefaultQuota() == null) {
            log.info("No layer quotas defined nor default quota. Disk quota monitor is disabled.");
        } else {
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

    private void validateConfig() {
        Quota defaultQuota = quotaConfig.getDefaultQuota();
        if (defaultQuota != null) {
            try {
                validateQuota(defaultQuota);
            } catch (IllegalArgumentException e) {
                log.error("Default disk quota configuration error: " + e.getMessage());
                throw e;
            }
        }

        if (quotaConfig.getLayerQuotas() != null) {
            for (LayerQuota lq : new ArrayList<LayerQuota>(quotaConfig.getLayerQuotas())) {
                validateLayerQuota(lq);
            }
        }
    }

    private void validateLayerQuota(LayerQuota lq) {
        String layer = lq.getLayer();
        try {
            tileLayerDispatcher.getTileLayer(layer);
        } catch (GeoWebCacheException e) {
            log.error("LayerQuota configuration error: layer " + layer
                    + " does not exist. Removing quota from runtime configuration.", e);
            quotaConfig.remove(lq);
        }

        Quota quota = lq.getQuota();
        try {
            validateQuota(quota);
        } catch (IllegalArgumentException e) {
            log.error("LayerQuota configuration error for layer " + layer + ". Error message is: "
                    + e.getMessage() + ". Quota removed from runtime configuration.");
            quotaConfig.remove(lq);
        }
    }

    private void validateQuota(Quota quota) throws IllegalArgumentException {
        if (quota == null) {
            throw new IllegalArgumentException("No quota defined");
        }
        double limit = quota.getLimit();
        if (limit < 0) {
            throw new IllegalArgumentException("Limit shall be >= 0: " + limit);
        }
        StorageUnit units = quota.getUnits();
        if (units == null) {
            throw new IllegalArgumentException("No storage units specified");
        }
        String expirationPolicyName = quota.getExpirationPolicy();
        if (expirationPolicyName == null) {
            throw new IllegalArgumentException("No expiration policy specified");
        }
        try {
            getExpirationPolicy(expirationPolicyName);
        } catch (NoSuchElementException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        log.debug("Quota validated: " + quota);
    }

    @SuppressWarnings("unchecked")
    private LayerQuotaExpirationPolicy getExpirationPolicy(String expirationPolicyName) {
        Map<String, LayerQuotaExpirationPolicy> expirationPolicies;
        expirationPolicies = context.getBeansOfType(LayerQuotaExpirationPolicy.class);
        for (LayerQuotaExpirationPolicy policy : expirationPolicies.values()) {
            if (policy.getName().equals(expirationPolicyName)) {
                return policy;
            }
        }
        throw new NoSuchElementException("No " + LayerQuotaExpirationPolicy.class.getName()
                + " found named '" + expirationPolicyName
                + "' in app context. Check your configuration file " + configFile.toExternalForm()
                + " and make sure the quota expiration policies defined match the ones available.");
    }

    private URL findConfFile(String cachePath) throws GeoWebCacheException {
        File configDir = determineConfigDirH(cachePath);

        URL resource = null;

        if (configDir == null) {

            resource = getClass().getResource("/" + CONFIGURATION_FILE_NAME);

        } else {
            File xmlFile = null;
            xmlFile = new File(configDir.getAbsolutePath() + File.separator
                    + CONFIGURATION_FILE_NAME);
            log.info("Found configuration file in " + configDir.getAbsolutePath());
            try {
                resource = xmlFile.toURI().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        if (resource == null) {
            log.debug("Unable to determine location of " + CONFIGURATION_FILE_NAME + ".");
        }

        return resource;
    }

    private File determineConfigDirH(String defaultPath) {
        String baseDir = context.getServletContext().getRealPath("");

        File configH = null;
        /*
         * Try 1) environment variables 2) standard paths 3) class path /geowebcache-diskquota.xml
         */
        if (defaultPath != null) {
            File tmpPath = new File(defaultPath + File.separator + CONFIGURATION_FILE_NAME);
            if (tmpPath.exists()) {
                configH = new File(tmpPath.getParent());
            }
        }

        // Finally, try "standard" paths if we have to.
        if (configH == null) {
            for (int i = 0; i < CONFIGURATION_REL_PATHS.length; i++) {
                String relPath = CONFIGURATION_REL_PATHS[i];
                if (File.separator.equals("\\")) {
                    relPath = relPath.replace("/", "\\");
                }

                File tmpPath = new File(baseDir + relPath + File.separator
                        + CONFIGURATION_FILE_NAME);

                if (tmpPath.exists() && tmpPath.canRead()) {
                    log.info("No configuration directory was specified, using "
                            + tmpPath.getAbsolutePath());
                    configH = new File(baseDir + relPath);
                }
            }
        }
        if (configH == null) {
            log.info("Failed to find " + CONFIGURATION_FILE_NAME
                    + ". This is not a problem unless you are trying "
                    + "to use a custom XML configuration file.");
        } else {
            log.debug("Configuration directory set to: " + configH.getAbsolutePath());

            if (!configH.exists() || !configH.canRead()) {
                log.error("Configuration file cannot be read or does not exist!");
            }
        }

        return configH;
    }

}
