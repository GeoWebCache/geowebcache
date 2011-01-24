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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
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
import com.thoughtworks.xstream.XStreamException;

/**
 * Utility class to load the disk quota configuration
 * <p>
 * An instance of this class is expected to be configured as a spring bean and then passed over as a
 * constructor parameter to {@link DiskQuotaMonitor}.
 * </p>
 * <p>
 * When {@link #loadConfig()} is called, a file named {@code geowebcache-diskquota.xml} will be
 * looked up for in the cache directory as specified by
 * {@link DefaultStorageFinder#getDefaultPath()}. The configuration file must adhere to the
 * {@code geowebcache-diskquota.xsd} schema.
 * </p>
 * 
 * @author Gabriel Roldan
 * 
 */
public class ConfigLoader {

    private static final Log log = LogFactory.getLog(ConfigLoader.class);

    private static final String CONFIGURATION_FILE_NAME = "geowebcache-diskquota.xml";

    private final WebApplicationContext context;

    private final TileLayerDispatcher tileLayerDispatcher;

    private final DefaultStorageFinder storageFinder;

    private final Map<String, ExpirationPolicy> expirationPolicyCache;

    /**
     * 
     * @param storageFinder
     *            used to get the location of the cache directory
     * @param contextProvider
     *            used to look up registered instances of {@link ExpirationPolicy} and to aid in
     *            determining the location of the {@code geowebcache-diskquota.xml} configuration
     *            file
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
        this.expirationPolicyCache = new HashMap<String, ExpirationPolicy>();
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
        final File configFile = new File(rootCacheDir, CONFIGURATION_FILE_NAME);
        final File tmpConfigFile = new File(rootCacheDir, CONFIGURATION_FILE_NAME + ".tmp");
        log.debug("Saving disk quota config to " + configFile.getAbsolutePath());
        OutputStream configOut = new FileOutputStream(tmpConfigFile);
        try {
            xStream.toXML(config, new OutputStreamWriter(configOut, "UTF-8"));
        } catch (RuntimeException e) {
            log.error("Error saving DiskQuota config to temp file :"
                    + tmpConfigFile.getAbsolutePath());
        } finally {
            configOut.close();
        }
        configFile.delete();
        if (!tmpConfigFile.renameTo(configFile)) {
            throw new ConfigurationException("Couldn't save disk quota config file "
                    + configFile.getAbsolutePath());
        }
    }

    public DiskQuotaConfig loadConfig() throws IOException, ConfigurationException {
        DiskQuotaConfig quotaConfig;
        final File configFile = getConfigResource();
        if (!configFile.exists()) {
            log.info("DiskQuota configuration not found: " + configFile.getAbsolutePath());
            quotaConfig = new DiskQuotaConfig();
        } else {
            log.info("Quota config is: " + configFile.getAbsolutePath());
            InputStream configIn = new FileInputStream(configFile);
            try {
                quotaConfig = loadConfiguration(configIn);
            } catch (RuntimeException e) {
                log.error(
                        "Error loading DiskQuota configuration from "
                                + configFile.getAbsolutePath() + ": " + e.getMessage()
                                + ". Deferring to a default (disabled) configuration", e);
                quotaConfig = new DiskQuotaConfig();
            } finally {
                configIn.close();
            }
        }
        // find out the global expiration policy, if set
        if (null != quotaConfig.getGlobalExpirationPolicyName()) {
            String expirationPolicyName = quotaConfig.getGlobalExpirationPolicyName();
            ExpirationPolicy policy = findExpirationPolicy(expirationPolicyName);
            quotaConfig.setGlobalExpirationPolicy(policy);
            addUnconfiguredLayerQuotas(quotaConfig);
        }

        validateConfig(quotaConfig);

        // XStream xstream = getConfiguredXStream();
        // xstream.toXML(quotaConfig, System.out);

        return quotaConfig;
    }

    private void addUnconfiguredLayerQuotas(DiskQuotaConfig quotaConfig) {
        final List<LayerQuota> configured = new ArrayList<LayerQuota>(quotaConfig.getLayerQuotas());

        Map<String, TileLayer> tileLayers = tileLayerDispatcher.getLayers();

        for (Map.Entry<String, TileLayer> entry : tileLayers.entrySet()) {
            String layerName = entry.getKey();
            if (null == quotaConfig.getLayerQuota(layerName)) {
                LayerQuota layerQuota = new LayerQuota(layerName, null);
                configured.add(layerQuota);
            }
        }
        quotaConfig.setLayerQuotas(configured);
    }

    private File getConfigResource() throws ConfigurationException, FileNotFoundException {
        String cachePath;
        try {
            cachePath = storageFinder.getDefaultPath();
        } catch (StorageException e) {
            throw new ConfigurationException(e.getMessage());
        }

        File file = new File(cachePath, CONFIGURATION_FILE_NAME);

        return file;
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

        final String expirationPolicyName = lq.getExpirationPolicyName();
        if (expirationPolicyName == null) {
            // if expiration policy is not defined, then there should be no quota defined either,
            // as it means the layer is managed by the global expiration policy, if any
            if (lq.getQuota() != null) {
                throw new ConfigurationException("Layer " + lq.getLayer()
                        + " has no expiration policy, but does have a quota defined. "
                        + "Either both or neither should be present");
            }
            return;
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

    public ExpirationPolicy findExpirationPolicy(final String expirationPolicyName) {

        ExpirationPolicy policy = getExpirationPolicies().get(expirationPolicyName);

        if (policy == null) {
            throw new NoSuchElementException("No " + ExpirationPolicy.class.getName()
                    + " found named '" + expirationPolicyName + "' in app context.");
        }
        return policy;
    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, ExpirationPolicy> getExpirationPolicies() {
        if (expirationPolicyCache.isEmpty()) {
            Map<String, ExpirationPolicy> expirationPolicies;
            expirationPolicies = context.getBeansOfType(ExpirationPolicy.class);
            for (ExpirationPolicy p : expirationPolicies.values()) {
                expirationPolicyCache.put(p.getName(), p);
            }
        }
        return new HashMap<String, ExpirationPolicy>(expirationPolicyCache);
    }

    private DiskQuotaConfig loadConfiguration(final InputStream configStream)
            throws XStreamException {
        XStream xstream = getConfiguredXStream();
        Reader reader;
        try {
            reader = new InputStreamReader(configStream, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        DiskQuotaConfig fromXML = (DiskQuotaConfig) xstream.fromXML(reader);
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
     * @param fileNameRelPath
     * @return
     * @throws IOException
     */
    public OutputStream getStorageOutputStream(String... fileNameRelPath) throws IOException {
        File rootCacheDir = getFileStorageDir(fileNameRelPath);
        String fileName = fileNameRelPath[fileNameRelPath.length - 1];
        File configFile = new File(rootCacheDir, fileName);
        return new FileOutputStream(configFile);
    }

    /**
     * Opens a stream over an existing file relative to the cache storage folder
     * 
     * @param fileNameRelPath
     *            the file name relative to the cache storage folder to open
     * @return
     * @throws IOException
     *             if {@code fileName} doesn't exist
     */
    public InputStream getStorageInputStream(String... fileNameRelPath) throws IOException {
        File rootCacheDir = getFileStorageDir(fileNameRelPath);
        String fileName = fileNameRelPath[fileNameRelPath.length - 1];
        File configFile = new File(rootCacheDir, fileName);
        return new FileInputStream(configFile);
    }

    /**
     * @param fileNameRelPath
     *            file path relative to the cache storage directory, where the last entry is the
     *            file name and any previous one directory names
     * @return
     * @throws StorageException
     */
    private File getFileStorageDir(String[] fileNameRelPath) throws StorageException {
        File parentDir = getRootCacheDir();
        for (int i = 0; i < fileNameRelPath.length - 1; i++) {
            parentDir = new File(parentDir, fileNameRelPath[i]);
        }
        parentDir.mkdirs();
        return parentDir;
    }

    public File getRootCacheDir() throws StorageException {
        return new File(storageFinder.getDefaultPath());
    }
}
