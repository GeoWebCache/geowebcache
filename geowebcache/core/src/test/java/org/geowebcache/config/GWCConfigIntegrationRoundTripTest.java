package org.geowebcache.config;

import org.apache.commons.io.FileUtils;
import org.geowebcache.grid.GridSetBroker;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.geowebcache.config.GWCConfigIntegrationTestData.*;
import static org.junit.Assert.assertTrue;


public class GWCConfigIntegrationRoundTripTest {

    //TODO: Add new tests as new configuration implementations are added
    /**
     * Tests {@link XMLConfiguration} persistence by initializing a configuration, constructing a new configuration from
     * the resulting geowebcache.xml, and validating some basic assumptions about the new config.
     *
     * @throws Exception
     */
    @Test
    public void testXMLConfiguration() throws Exception {
        GWCXMLConfigIntegrationTestSupport testSupport = new GWCXMLConfigIntegrationTestSupport();
        GWCConfigIntegrationTestData.setUpTestData(testSupport);

        File configDirCopy = Files.createTempDirectory("gwc").toFile();
        File configFileCopy = new File(configDirCopy, "geowebcache.xml");
        configDirCopy.deleteOnExit();

        FileUtils.copyFile(testSupport.configFile, configFileCopy);
        XMLConfiguration configCopy = new XMLConfiguration(null, configDirCopy.getAbsolutePath());
        configCopy.initialize(new GridSetBroker(true, true));

        assertTileLayerConfiguration(configCopy);
        assertGridSetConfiguration(configCopy);
        assertBlobStoreConfiguration(configCopy);
    }

    private void assertTileLayerConfiguration(TileLayerConfiguration config) {
        List<String> layerNames = Arrays.asList(LAYERS);
        for (String layerName : config.getLayerNames()) {
            assertTrue(layerNames.contains(layerName));
        }
        assertEquals(LAYERS.length, config.getLayerNames().size());
        assertEquals(LAYERS.length, config.getLayerCount());
    }

    private void assertGridSetConfiguration(GridSetConfiguration config) {
        //TODO: There are a number of default gridsets, not tracked by the configuration; handle these

        //TODO: Fill in this test once the GridSetConfiguration API is complete
    }

    private void assertBlobStoreConfiguration(BlobStoreConfiguration config) {
        List<BlobStoreInfo> blobStores = config.getBlobStores();
        List<String> blobStoreNames = Arrays.asList(BLOBSTORES);
        for (BlobStoreInfo blobStoreConfig : blobStores) {
            assertTrue(blobStoreNames.contains(blobStoreConfig.getName()));
        }
        assertEquals(BLOBSTORES.length, blobStores.size());
    }
}
