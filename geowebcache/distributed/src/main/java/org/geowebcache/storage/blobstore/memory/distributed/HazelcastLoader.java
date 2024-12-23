/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.storage.blobstore.memory.distributed;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.geotools.util.logging.Logging;
import org.geotools.xml.XMLUtils;
import org.springframework.beans.factory.InitializingBean;

/**
 * This class is used for handling configuration of the Hazelcast cluster. User can directly inject an Hazelcast
 * instance or can setup a file called hazelcast.xml and define its directory with the hazelcast.config.dir Java
 * property. Note that the configuration must contain a map with name "CacheProviderMap" with a specific size in MB, an
 * eviction policy equal to LRU or LFU. Also if NearCache is enabled, user must be careful that the max size is not
 * bigger or equal to Integer.MAX_VALUE
 *
 * @author Nicola Lagomarsini Geosolutions
 */
public class HazelcastLoader implements InitializingBean {
    /** {@link Logger} object used for logging exceptions */
    private static final Logger LOGGER = Logging.getLogger(HazelcastLoader.class.getName());

    /** Property name for the Hazelcast property file */
    public static final String HAZELCAST_CONFIG_DIR = "hazelcast.config.dir";

    /** Name of the Hazelcast XML file to use */
    public static final String HAZELCAST_NAME = "hazelcast.xml";

    /** Hazelcast instance to pass to the {@link HazelcastCacheProvider} class */
    private HazelcastInstance instance;

    // Disable Hazelcast's XXE protection if the XML libraries don't support JAXP 1.5
    static {
        if (System.getProperty("hazelcast.ignoreXxeProtectionFailures") == null) {
            try {
                XMLUtils.checkSupportForJAXP15Properties();
            } catch (IllegalStateException e) {
                LOGGER.warning("Disabling Hazelcast XXE protection because " + e.getMessage());
                System.setProperty("hazelcast.ignoreXxeProtectionFailures", "true");
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (instance == null) {
            // Search for the Hazelcast configuration directory
            String hazelDirPath = System.getProperty(HAZELCAST_CONFIG_DIR);
            if (hazelDirPath != null) {
                File hazelCastDir = new File(hazelDirPath);
                if (hazelCastDir.exists() && hazelCastDir.isDirectory() && hazelCastDir.canRead()) {
                    FileFilter filter = new NameFileFilter(HAZELCAST_NAME);
                    File[] files = hazelCastDir.listFiles(filter);
                    if (files != null && files.length > 0) {
                        File hazelCastConf = files[0];
                        Config config = null;
                        try (InputStream stream = new FileInputStream(hazelCastConf)) {
                            config = new XmlConfigBuilder(stream).build();
                        }
                        // Ensure the configuration is accepted
                        if (configAccepted(config)) {
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.fine("Hazelcast instance validated");
                            }
                            instance = Hazelcast.newHazelcastInstance(config);
                        } else {
                            if (LOGGER.isLoggable(Level.INFO)) {
                                LOGGER.info("No mapping for CacheProvider Map is present");
                            }
                        }
                    }
                }
            }
        } else if (!configAccepted(instance.getConfig())) {
            instance = null;
        }
        if (LOGGER.isLoggable(Level.FINE) && instance == null) {
            LOGGER.fine("Hazelcast instance invalid or not found");
        }
    }

    /**
     * Indicates if the Hazelcast instance has been configured
     *
     * @return a boolean indicating if hazelcast instance can be used or not
     */
    public boolean isConfigured() {
        return instance != null;
    }

    /** Setter for the Hazelcast instance */
    public void setInstance(HazelcastInstance instance) {
        this.instance = instance;
    }

    /**
     * Returns the Hazelcast instance to use
     *
     * @return Hazelcast instance if present or null
     */
    public HazelcastInstance getInstance() {
        return isConfigured() ? instance : null;
    }

    /**
     * Validation for an input {@link Config} object provided. This method ensures that the input configuration contains
     * a map with name "CacheProviderMap", contains a size configuration in Mb and related to the used Heap size and has
     * an eviction policy equal to LRU or LFU. If a NearCache object is defined it cannot have max size greater or equal
     * to {@link Integer}.MAX_VALUE
     */
    private boolean configAccepted(Config config) {
        boolean configAccepted = false;
        if (config != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Checking configuration");
            }
            // Check if the cache map is present
            if (config.getMapConfigs().containsKey(HazelcastCacheProvider.HAZELCAST_MAP_DEFINITION)) {
                MapConfig mapConfig = config.getMapConfig(HazelcastCacheProvider.HAZELCAST_MAP_DEFINITION);
                // Check size policy
                boolean sizeDefined = mapConfig.getEvictionConfig().getSize() > 0;
                boolean policyExists =
                        mapConfig.getEvictionConfig().getEvictionPolicy() != MapConfig.DEFAULT_EVICTION_POLICY;
                boolean sizeFromHeap = mapConfig.getEvictionConfig().getMaxSizePolicy() == MaxSizePolicy.USED_HEAP_SIZE;

                // Check Near Cache size
                boolean nearCacheAccepted = true;
                if (mapConfig.getNearCacheConfig() != null) {
                    NearCacheConfig conf = mapConfig.getNearCacheConfig();
                    nearCacheAccepted = conf.getEvictionConfig().getSize() < Integer.MAX_VALUE;
                }

                if (sizeDefined && policyExists && sizeFromHeap && nearCacheAccepted) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("Hazelcast config validated");
                    }
                    configAccepted = true;
                }
            }
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("No configuration provided");
            }
        }

        return configAccepted;
    }
}
