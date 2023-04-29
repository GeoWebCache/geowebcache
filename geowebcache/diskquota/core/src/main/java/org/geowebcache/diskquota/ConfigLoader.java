/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.config.ConfigurationResourceProvider;
import org.geowebcache.config.XMLFileResourceProvider;
import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.StorageUnit;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.util.Assert;

/**
 * Utility class to load the disk quota configuration
 *
 * <p>An instance of this class is expected to be configured as a spring bean and then passed over
 * as a constructor parameter to {@link DiskQuotaMonitor}.
 *
 * <p>When {@link #loadConfig()} is called, a file named {@code geowebcache-diskquota.xml} will be
 * looked up for in the cache directory as specified by {@link
 * DefaultStorageFinder#getDefaultPath()}. The configuration file must adhere to the {@code
 * geowebcache-diskquota.xsd} schema.
 *
 * @author Gabriel Roldan
 */
public class ConfigLoader {

    private static final Logger log = Logging.getLogger(ConfigLoader.class.getName());

    private static final String CONFIGURATION_FILE_NAME = "geowebcache-diskquota.xml";

    private final TileLayerDispatcher tileLayerDispatcher;

    private final ConfigurationResourceProvider resourceProvider;

    private final DefaultStorageFinder storageFinder;

    /**
     * @param storageFinder used to get the location of the cache directory
     * @param contextProvider used to look up registered instances of {@link ExpirationPolicy} and
     *     to aid in determining the location of the {@code geowebcache-diskquota.xml} configuration
     *     file
     * @param tld used only to validate the presence of a layer at {@link #loadConfig()} and ignore
     *     the layer quota definition if the {@link TileLayer} does not exist
     */
    public ConfigLoader(
            final DefaultStorageFinder storageFinder,
            final ApplicationContextProvider contextProvider,
            final TileLayerDispatcher tld)
            throws ConfigurationException {
        this(
                new XMLFileResourceProvider(
                        CONFIGURATION_FILE_NAME, contextProvider, null, storageFinder),
                storageFinder,
                tld);
    }

    /**
     * @param resourceProvider provides custom configuration resource
     * @param storageFinder used to get the location of the cache directory
     * @param tld used only to validate the presence of a layer at {@link #loadConfig()} and ignore
     *     the layer quota definition if the {@link TileLayer} does not exist
     */
    public ConfigLoader(
            final ConfigurationResourceProvider resourceProvider,
            final DefaultStorageFinder storageFinder,
            final TileLayerDispatcher tld)
            throws ConfigurationException {
        this.resourceProvider = resourceProvider;
        this.storageFinder = storageFinder;
        this.tileLayerDispatcher = tld;
    }

    /** Saves the configuration to the root cache directory */
    public void saveConfig(DiskQuotaConfig config) throws IOException, ConfigurationException {
        if (!resourceProvider.hasOutput()) {
            log.log(
                    Level.SEVERE,
                    "Unable to save DiskQuota to resource :" + resourceProvider.getLocation());
            return;
        }

        XStream xStream = getConfiguredXStream(new GeoWebCacheXStream());

        log.fine("Saving disk quota config to " + resourceProvider.getLocation());
        try (OutputStream configOut = resourceProvider.out()) {
            xStream.toXML(config, new OutputStreamWriter(configOut, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            log.log(
                    Level.SEVERE,
                    "Error saving DiskQuota config to file :" + resourceProvider.getLocation());
        }
    }

    public DiskQuotaConfig loadConfig() throws IOException, ConfigurationException {
        DiskQuotaConfig quotaConfig = null;

        if (resourceProvider.hasInput()) {
            log.config("Quota config is: " + resourceProvider.getLocation());

            try (InputStream configIn = resourceProvider.in()) {
                quotaConfig = loadConfiguration(configIn);
                if (null == quotaConfig) {
                    throw new ConfigurationException(
                            "Couldn't parse configuration file " + resourceProvider.getLocation());
                }
            } catch (IOException | RuntimeException e) {
                log.log(
                        Level.SEVERE,
                        "Error loading DiskQuota configuration from "
                                + resourceProvider.getLocation()
                                + ": "
                                + e.getMessage()
                                + ". Deferring to a default (disabled) configuration",
                        e);
            }
        } else {
            log.config(
                    "DiskQuota configuration is not readable: " + resourceProvider.getLocation());
        }

        if (quotaConfig == null) {
            quotaConfig = new DiskQuotaConfig();
        }

        // set default values
        quotaConfig.setDefaults();

        validateConfig(quotaConfig);

        return quotaConfig;
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
        int maxConcurrentCleanUps = quotaConfig.getMaxConcurrentCleanUps();
        if (maxConcurrentCleanUps <= 0) {
            throw new ConfigurationException(
                    "maxConcurrentCleanUps shall be specified as a positive integer");
        }

        if (null != quotaConfig.getLayerQuotas()) {
            for (LayerQuota lq : new ArrayList<>(quotaConfig.getLayerQuotas())) {
                if (null == lq.getQuota()) {
                    log.info(
                            "Configured quota for layer "
                                    + lq.getLayer()
                                    + " is null. Discarding it to be attached to the global quota");
                    quotaConfig.remove(lq);
                    continue;
                }

                validateLayerQuota(quotaConfig, lq);
            }
        }
    }

    private void validateLayerQuota(DiskQuotaConfig quotaConfig, LayerQuota lq)
            throws ConfigurationException {
        String layer = lq.getLayer();
        try {
            tileLayerDispatcher.getTileLayer(layer);
        } catch (GeoWebCacheException e) {
            log.log(
                    Level.SEVERE,
                    "LayerQuota configuration error: layer "
                            + layer
                            + " does not exist. Removing quota from runtime configuration.",
                    e);
            quotaConfig.remove(lq);
        }

        final ExpirationPolicy expirationPolicyName = lq.getExpirationPolicyName();
        if (expirationPolicyName == null) {
            // if expiration policy is not defined, then there should be no quota defined either,
            // as it means the layer is managed by the global expiration policy, if any
            if (lq.getQuota() != null) {
                throw new ConfigurationException(
                        "Layer "
                                + lq.getLayer()
                                + " has no expiration policy, but does have a quota defined. "
                                + "Either both or neither should be present");
            }
            return;
        }

        Quota quota = lq.getQuota();
        try {
            validateQuota(quota);
        } catch (ConfigurationException e) {
            log.log(
                    Level.SEVERE,
                    "LayerQuota configuration error for layer "
                            + layer
                            + ". Error message is: "
                            + e.getMessage()
                            + ". Quota removed from runtime configuration.");
            quotaConfig.remove(lq);
        }
    }

    private void validateQuota(Quota quota) throws ConfigurationException {
        if (quota == null) {
            throw new IllegalArgumentException("No quota defined");
        }
        BigInteger limit = quota.getBytes();
        if (limit.compareTo(BigInteger.ZERO) < 0) {
            throw new ConfigurationException("Limit shall be >= 0: " + limit + ". " + quota);
        }

        log.fine("Quota validated: " + quota);
    }

    private DiskQuotaConfig loadConfiguration(final InputStream configStream)
            throws XStreamException {
        XStream xstream = getConfiguredXStream(new GeoWebCacheXStream());
        try (Reader reader = new InputStreamReader(configStream, StandardCharsets.UTF_8)) {
            DiskQuotaConfig fromXML = loadConfiguration(reader, xstream);
            return fromXML;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static DiskQuotaConfig loadConfiguration(final Reader reader, XStream xstream) {
        DiskQuotaConfig fromXML = (DiskQuotaConfig) xstream.fromXML(reader);

        final GeoWebCacheEnvironment gwcEnvironment =
                GeoWebCacheExtensions.bean(GeoWebCacheEnvironment.class);

        if (gwcEnvironment != null && GeoWebCacheEnvironment.ALLOW_ENV_PARAMETRIZATION) {
            fromXML.setQuotaStore((String) gwcEnvironment.resolveValue(fromXML.getQuotaStore()));
        }

        return fromXML;
    }

    public static XStream getConfiguredXStream(XStream xs) {
        // Allow anything that's part of GWC Diskquota
        // TODO: replace this with a more narrow whitelist
        xs.allowTypesByWildcard(new String[] {"org.geowebcache.**"});

        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("gwcQuotaConfiguration", DiskQuotaConfig.class);
        xs.alias("layerQuotas", List.class);
        xs.alias("LayerQuota", LayerQuota.class);
        xs.alias("Quota", Quota.class);
        xs.registerConverter(new QuotaXSTreamConverter());
        return xs;
    }

    /** Opens an output stream for a file relative to the cache storage folder */
    public OutputStream getStorageOutputStream(String... fileNameRelPath)
            throws IOException, ConfigurationException {
        File rootCacheDir = getFileStorageDir(fileNameRelPath);
        String fileName = fileNameRelPath[fileNameRelPath.length - 1];
        File configFile = new File(rootCacheDir, fileName);
        return new FileOutputStream(configFile);
    }

    /**
     * Opens a stream over an existing file relative to the cache storage folder
     *
     * @param fileNameRelPath the file name relative to the cache storage folder to open
     * @throws IOException if {@code fileName} doesn't exist
     */
    public InputStream getStorageInputStream(String... fileNameRelPath)
            throws IOException, ConfigurationException {
        File rootCacheDir = getFileStorageDir(fileNameRelPath);
        String fileName = fileNameRelPath[fileNameRelPath.length - 1];
        File configFile = new File(rootCacheDir, fileName);
        return new FileInputStream(configFile);
    }

    /**
     * @param fileNameRelPath file path relative to the cache storage directory, where the last
     *     entry is the file name and any previous one directory names
     */
    private File getFileStorageDir(String[] fileNameRelPath) throws ConfigurationException {
        File parentDir = getRootCacheDir();
        for (int i = 0; i < fileNameRelPath.length - 1; i++) {
            parentDir = new File(parentDir, fileNameRelPath[i]);
        }
        parentDir.mkdirs();
        return parentDir;
    }

    public File getRootCacheDir() throws ConfigurationException {
        return new File(storageFinder.getDefaultPath());
    }

    /**
     * Handles XStream conversion of {@link Quota}s to persist them as {@code
     * <value>value</value><units>StorageUnit</units>} instead of plain byte count.
     *
     * @author groldan
     */
    private static final class QuotaXSTreamConverter implements Converter {
        /** @see com.thoughtworks.xstream.converters.ConverterMatcher#canConvert(java.lang.Class) */
        @Override
        public boolean canConvert(Class type) {
            return Quota.class.equals(type);
        }

        /**
         * @see
         *     com.thoughtworks.xstream.converters.Converter#unmarshal(com.thoughtworks.xstream.io.HierarchicalStreamReader,
         *     com.thoughtworks.xstream.converters.UnmarshallingContext)
         */
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {

            Quota quota = new Quota();

            reader.moveDown();
            String nodeName = reader.getNodeName();
            Assert.isTrue(
                    "value".equals(nodeName),
                    "Expected element name to be 'value' but was " + nodeName + " instead");

            String nodevalue = reader.getValue();
            double value = Double.parseDouble(nodevalue);
            reader.moveUp();

            reader.moveDown();
            nodeName = reader.getNodeName();
            Assert.isTrue(
                    "units".equals(nodeName),
                    "Expected to find a units element, but found " + nodeName + " instead");

            nodevalue = reader.getValue();
            StorageUnit unit = StorageUnit.valueOf(nodevalue);
            reader.moveUp();

            quota.setValue(value, unit);
            return quota;
        }

        /**
         * @see com.thoughtworks.xstream.converters.Converter#marshal(java.lang.Object,
         *     com.thoughtworks.xstream.io.HierarchicalStreamWriter,
         *     com.thoughtworks.xstream.converters.MarshallingContext)
         */
        @Override
        public void marshal(
                Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            Quota quota = (Quota) source;
            BigInteger bytes = quota.getBytes();
            StorageUnit unit = StorageUnit.bestFit(bytes);
            BigDecimal value = unit.fromBytes(bytes);

            writer.startNode("value");
            writer.setValue(value.toString());
            writer.endNode();

            writer.startNode("units");
            writer.setValue(unit.toString());
            writer.endNode();
        }
    }
}
