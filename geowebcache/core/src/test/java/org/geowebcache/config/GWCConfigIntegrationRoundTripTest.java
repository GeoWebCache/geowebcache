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

import static org.geowebcache.config.GWCConfigIntegrationTestData.BLOBSTORES;
import static org.geowebcache.config.GWCConfigIntegrationTestData.LAYERS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.geowebcache.grid.GridSetBroker;
import org.junit.Test;

public class GWCConfigIntegrationRoundTripTest {

    // TODO: Add new tests as new configuration implementations are added
    /**
     * Tests {@link XMLConfiguration} persistence by initializing a configuration, constructing a new configuration from
     * the resulting geowebcache.xml, and validating some basic assumptions about the new config.
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
        configCopy.setGridSetBroker(new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true))));
        configCopy.afterPropertiesSet();

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
        // TODO: There are a number of default gridsets, not tracked by the configuration; handle
        // these

        // TODO: Fill in this test once the GridSetConfiguration API is complete
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
