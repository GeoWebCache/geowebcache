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
 * Copyright 2018
 *
 */
package org.geowebcache.config;

import org.apache.commons.io.FileUtils;
import org.geowebcache.grid.GridSetBroker;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GWCXMLConfigIntegrationTestSupport extends GWCConfigIntegrationTestSupport {

    protected File configDir;
    protected File configFile;

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
        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        broker = new GridSetBroker(getGridSetConfigurations());
        config.setGridSetBroker(broker);
        config.deinitialize();
        config.reinitialize();
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
    public List<GridSetConfiguration> getGridSetConfigurations() {
        return Arrays.asList(new DefaultGridsets(true, true), config);
    }

    @Override
    public BlobStoreConfiguration getBlobStoreConfiguration() {
        return config;
    }

    @Override
    public GridSetConfiguration getWritableGridSetConfiguration() {
        return config;
    }
}
