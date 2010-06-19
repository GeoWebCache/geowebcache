package org.geowebcache.diskquota;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.layer.TileLayer;
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
public class ConfigLoader {

    private static final Log log = LogFactory.getLog(ConfigLoader.class);

    private static final String CONFIGURATION_FILE_NAME = "geowebcache-diskquota.xml";

    private static final String[] CONFIGURATION_REL_PATHS = { "/WEB-INF/classes", "/../resources" };

    private final WebApplicationContext context;

    private final TileLayerDispatcher tileLayerDispatcher;

    private final DefaultStorageFinder storageFinder;

    private final Map<String, LayerQuotaExpirationPolicy> enabledPolicies;

    /**
     * 
     * @param storageFinder
     *            used to get the location of the cache directory
     * @param contextProvider
     *            used to look up registered instances of {@link LayerQuotaExpirationPolicy} and to
     *            aid in determining the location of the {@code geowebcache-diskquota.xml}
     *            configuration file
     * @param tld
     *            used only to validate the presence of a layer at {@link #loadConfig()} and ignore
     *            the layer quota definition if the {@link TileLayer} does not exist
     * @throws IOException
     */
    public ConfigLoader(final DefaultStorageFinder storageFinder,
            final ApplicationContextProvider contextProvider, final TileLayerDispatcher tld)
            throws IOException {

        this.storageFinder = storageFinder;
        this.context = contextProvider.getApplicationContext();
        this.tileLayerDispatcher = tld;
        this.enabledPolicies = new HashMap<String, LayerQuotaExpirationPolicy>();
    }

    /**
     * Saves the configuration to the root cache directory
     * 
     * @param config
     * @throws IOException
     * @throws ConfigurationException
     */
    public void saveConfig(DiskQuotaConfig config) throws IOException, ConfigurationException {
        File rootCacheDir = getRootCacheDir();
        XStream xStream = getConfiguredXStream();
        File configFile = new File(rootCacheDir, CONFIGURATION_FILE_NAME);
        log.debug("Saving disk quota config to " + configFile.getAbsolutePath());
        OutputStream configOut = new FileOutputStream(configFile);
        try {
            xStream.toXML(config, configOut);
        } finally {
            configOut.close();
        }
    }

    public DiskQuotaConfig loadConfig() throws IOException, ConfigurationException {
        URL configFile = getConfigResource();

        InputStream configIn = configFile.openStream();
        DiskQuotaConfig quotaConfig;
        try {
            quotaConfig = loadConfiguration(configIn);
        } finally {
            configIn.close();
        }

        validateConfig(quotaConfig);

        XStream xstream = getConfiguredXStream();
        log.info("Quota config is: " + configFile.toExternalForm());
        xstream.toXML(quotaConfig, System.out);

        return quotaConfig;
    }

    private URL getConfigResource() throws ConfigurationException, FileNotFoundException {
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
        return configFile;
    }

    private void validateConfig(DiskQuotaConfig quotaConfig) throws ConfigurationException {
        int cacheCleanUpFrequency = quotaConfig.getCacheCleanUpFrequency();
        if (cacheCleanUpFrequency <= 0) {
            throw new ConfigurationException("cacheCleanUpFrequency shall be a positive integer");
        }
        TimeUnit cacheCleanUpUnits = quotaConfig.getCacheCleanUpUnits();
        if (cacheCleanUpUnits == null) {
            throw new ConfigurationException(
                    "cacheCleanUpUnits shall be specified. Expected one of SECONDS, MINUTES, HOURS, DAYS. Got null");
        }
        int diskBlockSize = quotaConfig.getDiskBlockSize();
        if (diskBlockSize <= 0) {
            throw new ConfigurationException(
                    "Disk block size shall be specified and be a positive integer");
        }

        int maxConcurrentCleanUps = quotaConfig.getMaxConcurrentCleanUps();
        if (maxConcurrentCleanUps <= 0) {
            throw new ConfigurationException(
                    "maxConcurrentCleanUps shall be specified as a positive integer");
        }

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
            findExpirationPolicy(expirationPolicyName);
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
        BigDecimal limit = quota.getValue();
        if (limit.compareTo(BigDecimal.ZERO) < 0) {
            throw new ConfigurationException("Limit shall be >= 0: " + limit + ". " + quota);
        }
        StorageUnit units = quota.getUnits();
        if (units == null) {
            throw new ConfigurationException("No storage units specified: " + quota);
        }
        log.debug("Quota validated: " + quota);
    }

    @SuppressWarnings("unchecked")
    public LayerQuotaExpirationPolicy findExpirationPolicy(final String expirationPolicyName) {

        LayerQuotaExpirationPolicy policy = this.enabledPolicies.get(expirationPolicyName);

        if (policy == null) {
            Map<String, LayerQuotaExpirationPolicy> expirationPolicies;
            expirationPolicies = context.getBeansOfType(LayerQuotaExpirationPolicy.class);
            for (LayerQuotaExpirationPolicy p : expirationPolicies.values()) {
                if (p.getName().equals(expirationPolicyName)) {
                    enabledPolicies.put(p.getName(), p);
                    return p;
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
            log.debug("Found configuration file in " + configDir.getAbsolutePath());
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
        if (configH != null) {
            log.debug("Configuration directory set to: " + configH.getAbsolutePath());

            if (!configH.exists() || !configH.canRead()) {
                log.error("Configuration file cannot be read or does not exist!");
            }
        }

        return configH;
    }

    private DiskQuotaConfig loadConfiguration(final InputStream configStream) {
        XStream xstream = getConfiguredXStream();
        DiskQuotaConfig fromXML = (DiskQuotaConfig) xstream.fromXML(configStream);
        return fromXML;
    }

    private static XStream getConfiguredXStream() {
        XStream xs = new XStream();
        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("gwcQuotaConfiguration", DiskQuotaConfig.class);
        xs.alias("layerQuotas", List.class);
        xs.alias("LayerQuota", LayerQuota.class);
        xs.alias("Quota", Quota.class);
        return xs;
    }

    /**
     * Opens an output stream for a file relative to the cache storage folder
     * 
     * @param fileName
     * @return
     * @throws IOException
     */
    public OutputStream getStorageOutputStream(String fileName) throws IOException {
        File rootCacheDir = getRootCacheDir();
        File configFile = new File(rootCacheDir, fileName);
        return new FileOutputStream(configFile);
    }

    /**
     * Opens a stream over an existing file relative to the cache storage folder
     * 
     * @param fileName
     *            the file name relative to the cache storage folder to open
     * @return
     * @throws IOException
     *             if {@code fileName} doesn't exist
     */
    public InputStream getStorageInputStream(String fileName) throws IOException {
        File rootCacheDir = getRootCacheDir();
        File configFile = new File(rootCacheDir, fileName);
        return new FileInputStream(configFile);
    }

    public File getRootCacheDir() throws StorageException {
        return new File(storageFinder.getDefaultPath());
    }
}
