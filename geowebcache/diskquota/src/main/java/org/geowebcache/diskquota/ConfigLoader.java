package org.geowebcache.diskquota;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.web.context.WebApplicationContext;

import com.thoughtworks.xstream.XStream;

/**
 * Utility class to load the disk quota configuration
 * <p>
 * An instance of this class is expected to be configured as a spring bean and then passed over as a
 * constructor parameter to {@link DiskQuotaMonitor}.
 * </p>
 * <p>
 * When {@link #loadConfig()} is called, a file named {@code geowebcache-diskquota.xml} will be
 * looked up for in the following locations (in order):
 * <ul>
 * <li>Default path, as specified by {@link DefaultStorageFinder#getDefaultPath()}
 * <li>/WEB-INF/classes
 * <li>class path:/geowebcache-diskquota.xml
 * </ul>
 * The configuration file must adhere to the {@code geowebcache-diskquota.xsd} schema.
 * </p>
 * 
 * @author Gabriel Roldan
 * 
 */
class ConfigLoader {

    private static final Log log = LogFactory.getLog(ConfigLoader.class);

    private static final String CONFIGURATION_FILE_NAME = "geowebcache-diskquota.xml";

    private static final String[] CONFIGURATION_REL_PATHS = { "/WEB-INF/classes", "/../resources" };

    private final WebApplicationContext context;

    private final TileLayerDispatcher tileLayerDispatcher;

    private final DefaultStorageFinder storageFinder;

    private final Map<String, LayerQuotaExpirationPolicy> enabledPolicies;

    public ConfigLoader(DefaultStorageFinder storageFinder,
            ApplicationContextProvider contextProvider, TileLayerDispatcher tld) throws IOException {

        this.storageFinder = storageFinder;
        this.context = contextProvider.getApplicationContext();
        this.tileLayerDispatcher = tld;
        this.enabledPolicies = new HashMap<String, LayerQuotaExpirationPolicy>();
    }

    public DiskQuotaConfig loadConfig() throws IOException, ConfigurationException {
        String cachePath;
        try {
            cachePath = storageFinder.getDefaultPath();
        } catch (StorageException e) {
            throw new ConfigurationException(e.getMessage());
        }
        URL configFile;
        try {
            configFile = findConfFile(cachePath);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }

        if (configFile == null) {
            throw new FileNotFoundException("Found no " + CONFIGURATION_FILE_NAME
                    + " file. Disk Quota is disabled.");
        }

        InputStream configIn = configFile.openStream();
        DiskQuotaConfig quotaConfig;
        try {
            quotaConfig = loadConfiguration(configIn);
        } finally {
            configIn.close();
        }

        validateConfig(quotaConfig);
        return quotaConfig;
    }

    private void validateConfig(DiskQuotaConfig quotaConfig) throws ConfigurationException {
        for (LayerQuota lq : new ArrayList<LayerQuota>(quotaConfig.getLayerQuotas())) {
            validateLayerQuota(quotaConfig, lq);
        }
    }

    private void validateLayerQuota(DiskQuotaConfig quotaConfig, LayerQuota lq)
            throws ConfigurationException {
        String layer = lq.getLayer();
        try {
            tileLayerDispatcher.getTileLayer(layer);
        } catch (GeoWebCacheException e) {
            log.error("LayerQuota configuration error: layer " + layer
                    + " does not exist. Removing quota from runtime configuration.", e);
            quotaConfig.remove(lq);
        }

        String expirationPolicyName = lq.getExpirationPolicyName();
        if (expirationPolicyName == null) {
            throw new ConfigurationException("No expiration policy specified: " + lq);
        }
        try {
            getExpirationPolicy(expirationPolicyName);
        } catch (NoSuchElementException e) {
            throw new ConfigurationException(e.getMessage());
        }

        Quota quota = lq.getQuota();
        try {
            validateQuota(quota);
        } catch (ConfigurationException e) {
            log.error("LayerQuota configuration error for layer " + layer + ". Error message is: "
                    + e.getMessage() + ". Quota removed from runtime configuration.");
            quotaConfig.remove(lq);
        }
    }

    private void validateQuota(Quota quota) throws ConfigurationException {
        if (quota == null) {
            throw new IllegalArgumentException("No quota defined");
        }
        double limit = quota.getValue();
        if (limit < 0) {
            throw new ConfigurationException("Limit shall be >= 0: " + limit + ". " + quota);
        }
        StorageUnit units = quota.getUnits();
        if (units == null) {
            throw new ConfigurationException("No storage units specified: " + quota);
        }
        log.debug("Quota validated: " + quota);
    }

    @SuppressWarnings("unchecked")
    public LayerQuotaExpirationPolicy getExpirationPolicy(final String expirationPolicyName) {

        LayerQuotaExpirationPolicy policy = this.enabledPolicies.get(expirationPolicyName);

        if (policy == null) {
            Map<String, LayerQuotaExpirationPolicy> expirationPolicies;
            expirationPolicies = context.getBeansOfType(LayerQuotaExpirationPolicy.class);
            for (LayerQuotaExpirationPolicy p : expirationPolicies.values()) {
                if (p.getName().equals(expirationPolicyName)) {
                    enabledPolicies.put(p.getName(), p);
                    return policy;
                }
            }
        } else {
            return policy;
        }
        throw new NoSuchElementException("No " + LayerQuotaExpirationPolicy.class.getName()
                + " found named '" + expirationPolicyName + "' in app context.");
    }

    private URL findConfFile(String cachePath) throws GeoWebCacheException {
        File configDir = determineConfigDir(cachePath);

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

    private File determineConfigDir(String defaultPath) {
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

    private DiskQuotaConfig loadConfiguration(final InputStream configStream) {
        XStream xstream = getConfiguredXStram();
        DiskQuotaConfig fromXML = (DiskQuotaConfig) xstream.fromXML(configStream);
        return fromXML;
    }

    private static XStream getConfiguredXStram() {
        XStream xs = new XStream();
        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("gwcQuotaConfiguration", DiskQuotaConfig.class);
        xs.alias("layerQuotas", List.class);
        xs.alias("LayerQuota", LayerQuota.class);
        xs.alias("Quota", Quota.class);
        return xs;
    }
}
