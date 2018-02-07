package org.geowebcache.config;

import org.apache.commons.io.FileUtils;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.locks.LockProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.*;

public class ServerConfigurationTest {

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
        config.setIsRuntimeStatsEnabled(false);
        runtimeStats = config.isRuntimeStatsEnabled();
        assertFalse(runtimeStats);

        LockProvider lockProvider = config.getLockProvider();
        assertNotNull(lockProvider);

        Boolean fullWMS = config.getfullWMS();
        assertNull(fullWMS);
        config.setFullWMS(true);
        fullWMS = config.getfullWMS();
        assertTrue(fullWMS);

        Boolean wmtsCiteCompliant = config.isWmtsCiteCompliant();
        assertFalse(wmtsCiteCompliant);
        config.setWmtsCiteStrictCompliant(true);
        wmtsCiteCompliant = config.isWmtsCiteCompliant();
        assertTrue(wmtsCiteCompliant);

        // Initialize to reload the configuration from the XML file and test persistence
        config.initialize(gridSetBroker);
        ServiceInformation savedInfo = config.getServiceInformation();
        assertEquals(savedInfo.getProviderName(), "John Adams inc.");
        assertFalse(config.isRuntimeStatsEnabled());
        assertTrue(config.getfullWMS());
        assertTrue((config.isWmtsCiteCompliant()));





    }

    protected ServerConfiguration getConfig() throws Exception {
         if(configFile==null) {
             // create a temp XML config
             configDir = temp.getRoot();
             configFile = temp.newFile(XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME);
             // copy the example XML to the temp config file
             URL source = XMLConfiguration.class.getResource("geowebcache_190.xml");
             FileUtils.copyURLToFile(source, configFile);
             }
             // initialize the config with an XMLFileResourceProvider that uses the temp config file
        gridSetBroker = new GridSetBroker(true, true);
         ConfigurationResourceProvider configProvider =
             new XMLFileResourceProvider(XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME,
                 (WebApplicationContext)null, configDir.getAbsolutePath(), null);
         config = new XMLConfiguration(null, configProvider);
         config.initialize(gridSetBroker);
         return config;
    }
}
