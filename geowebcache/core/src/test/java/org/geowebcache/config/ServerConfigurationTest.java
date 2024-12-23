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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.util.TestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.web.context.WebApplicationContext;

public class ServerConfigurationTest {

    private static final String VERSION_PATTERN = "1(\\.\\d+)+(\\-\\w+)*";

    ServerConfiguration config;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File configDir;
    private File configFile;
    private GridSetBroker gridSetBroker;

    @Test
    public void serverConfigTest() throws Exception {
        config = getConfig();
        // testing various data in service inforomation
        ServiceInformation serviceInfo = config.getServiceInformation();
        assertEquals("GeoWebCache", serviceInfo.getTitle());
        assertEquals("John Smith inc.", serviceInfo.getServiceProvider().getProviderName());
        ServiceInformation newinfo = new ServiceInformation();
        newinfo.setProviderName("John Adams inc.");
        config.setServiceInformation(newinfo);
        assertEquals("John Adams inc.", config.getServiceInformation().getProviderName());

        Boolean runtimeStats = config.isRuntimeStatsEnabled();
        assertTrue(runtimeStats);
        config.setRuntimeStatsEnabled(false);
        runtimeStats = config.isRuntimeStatsEnabled();
        assertFalse(runtimeStats);

        LockProvider lockProvider = config.getLockProvider();
        assertNotNull(lockProvider);

        Boolean fullWMS = config.isFullWMS();
        assertNull(fullWMS);
        config.setFullWMS(true);
        fullWMS = config.isFullWMS();
        assertTrue(fullWMS);

        Boolean wmtsCiteCompliant = config.isWmtsCiteCompliant();
        assertFalse(wmtsCiteCompliant);
        config.setWmtsCiteCompliant(true);
        wmtsCiteCompliant = config.isWmtsCiteCompliant();
        assertTrue(wmtsCiteCompliant);

        Boolean cacheBypassAllowed = config.isCacheBypassAllowed();
        assertNull(cacheBypassAllowed);
        config.setCacheBypassAllowed(true);
        cacheBypassAllowed = config.isCacheBypassAllowed();
        assertTrue(cacheBypassAllowed);

        Integer backendTimeout = config.getBackendTimeout();
        assertEquals(backendTimeout, (Integer) 120);
        config.setBackendTimeout(60);
        backendTimeout = config.getBackendTimeout();
        assertEquals(backendTimeout, (Integer) 60);

        assertThat(config, hasProperty("version", TestUtils.matchesRegex(VERSION_PATTERN)));

        // Initialize to reload the configuration from the XML file and test persistence
        config.afterPropertiesSet();
        ServiceInformation savedInfo = config.getServiceInformation();
        assertEquals(savedInfo.getProviderName(), "John Adams inc.");
        assertFalse(config.isRuntimeStatsEnabled());
        assertTrue(config.isFullWMS());
        assertTrue((config.isWmtsCiteCompliant()));
        assertTrue(config.isCacheBypassAllowed());
        assertEquals(config.getBackendTimeout(), (Integer) 60);
        assertThat(config.getVersion(), TestUtils.matchesRegex(VERSION_PATTERN));
    }

    protected ServerConfiguration getConfig() throws Exception {
        if (configFile == null) {
            // create a temp XML config
            configDir = temp.getRoot();
            configFile = temp.newFile(XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME);
            // copy the example XML to the temp config file
            URL source = XMLConfiguration.class.getResource("geowebcache_190.xml");
            FileUtils.copyURLToFile(source, configFile);
        }
        // initialize the config with an XMLFileResourceProvider that uses the temp config file
        gridSetBroker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true)));
        ConfigurationResourceProvider configProvider = new XMLFileResourceProvider(
                XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME,
                (WebApplicationContext) null,
                configDir.getAbsolutePath(),
                null);
        config = new XMLConfiguration(null, configProvider);
        ((XMLConfiguration) config).setGridSetBroker(gridSetBroker);
        config.afterPropertiesSet();
        return config;
    }
}
