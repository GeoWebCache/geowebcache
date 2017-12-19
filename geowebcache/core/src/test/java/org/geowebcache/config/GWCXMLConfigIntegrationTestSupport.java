package org.geowebcache.config;

import org.apache.commons.io.FileUtils;
import org.geowebcache.grid.GridSetBroker;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class GWCXMLConfigIntegrationTestSupport extends GWCConfigIntegrationTestSupport {

    private File configDir;
    private File configFile;

    protected XMLConfiguration config;

    public GWCXMLConfigIntegrationTestSupport() throws Exception {
        configDir = Files.createTempDirectory("gwc").toFile();
        configFile = new File(configDir, "geowebcache.xml");
        configDir.deleteOnExit();

        resetConfiguration();
    }

    @Override
    public void resetConfiguration() throws Exception {
        if (configFile != null) {
            URL source = XMLConfiguration.class.getResource("geowebcache-empty.xml");
            FileUtils.copyURLToFile(source, configFile);
        }
        broker = new GridSetBroker(true, true);
        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.initialize(broker);
    }

    @Override
    public List<TileLayerConfiguration> getTileLayerConfigurations() {
        return Collections.singletonList(config);
    }

    @Override
    public ServerConfiguration getServerConfiguration() {
        return config;
    }

    @Override
    public GridSetConfiguration getGridSetConfiguration() {
        return config;
    }

    @Override
    public BlobStoreConfigurationCatalog getBlobStoreConfiguration() {
        return config;
    }
}
