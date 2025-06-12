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
package org.geowebcache.arcgis.layer;

import static org.geowebcache.util.TestUtils.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.XMLConfigurationLayerConformanceTest;
import org.geowebcache.config.XMLConfigurationProvider;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.TileLayer;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;

public class XMLConfigurationLayerConformanceWithArcGisLayersTest extends XMLConfigurationLayerConformanceTest {

    @Override
    public void setUpTestUnit() throws Exception {
        super.setUpTestUnit();
    }

    File resourceAsFile(String resource) {
        URL url = getClass().getResource(resource);
        File f;
        try {
            f = new File(url.toURI());
        } catch (URISyntaxException e) {
            f = new File(url.getPath());
        }
        return f;
    }

    @Override
    protected TileLayer getGoodInfo(String id, int rand) {
        ArcGISCacheLayer layer = new ArcGISCacheLayer(id);
        File tileScheme = resourceAsFile("/compactcache/Conf.xml");
        layer.setTilingScheme(tileScheme);
        layer.setBackendTimeout(rand);
        return layer;
    }

    @Override
    protected TileLayer getBadInfo(String id, int rand) {
        return new AbstractTileLayer() {
            {
                this.name = id;
            }

            @Override
            protected boolean initializeInternal(GridSetBroker gridSetBroker) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public String getStyles() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ConveyorTile getTile(ConveyorTile tile)
                    throws GeoWebCacheException, IOException, OutsideCoverageException {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException, IOException {
                // TODO Auto-generated method stub

            }

            @Override
            public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
                // TODO Auto-generated method stub
                return null;
            }
        };
    }

    @Override
    protected Matcher<TileLayer> infoEquals(TileLayer expected) {
        return new CustomMatcher<>(
                "ArcGISCacheLayer matching " + expected.getName() + " with " + expected.getBackendTimeout()) {

            @Override
            public boolean matches(Object item) {
                return item instanceof ArcGISCacheLayer agiscl
                        && agiscl.getBackendTimeout().equals(expected.getBackendTimeout());
            }
        };
    }

    @Override
    protected Matcher<TileLayer> infoEquals(int expected) {
        return new CustomMatcher<>("ArcGISCacheLayer with value" + expected) {

            @Override
            public boolean matches(Object item) {
                return item instanceof ArcGISCacheLayer agiscl
                        && agiscl.getBackendTimeout().equals(expected);
            }
        };
    }

    @Override
    protected String getExistingInfo() {
        return "testExisting";
    }

    @Override
    protected void doModifyInfo(TileLayer info, int rand) throws Exception {
        info.setBackendTimeout(rand);
    }

    @Override
    protected void makeConfigFile() throws Exception {
        if (configFile == null) {
            configDir = temp.getRoot();
            configFile = temp.newFile("geowebcache.xml");

            URL source = XMLConfigurationLayerConformanceWithArcGisLayersTest.class.getResource("geowebcache.xml");
            try (Stream<String> lines = Files.lines(Path.of(source.toURI()))) {
                List<String> replaced = lines.map(line -> {
                            String tilingSchemePath =
                                    resourceAsFile("/compactcache/Conf.xml").getAbsolutePath();
                            // no need to use replaceAll and involve regex
                            // replacement rules
                            return line.replace("TILING_SCHEME_PATH", tilingSchemePath);
                        })
                        .collect(Collectors.toList());
                Files.write(configFile.toPath(), replaced);
            }
        }
    }

    @Override
    protected TileLayerConfiguration getConfig(MockWepAppContextRule extensions) throws Exception {
        extensions.addBean(
                "ArcGISLayerConfigProvider", new ArcGISLayerXMLConfigurationProvider(), XMLConfigurationProvider.class);
        ArcGISCacheGridsetConfiguration arcGISCacheGridsetConfiguration = new ArcGISCacheGridsetConfiguration();
        extensions.addBean(
                "ArcGISLayerGridSetConfiguration",
                arcGISCacheGridsetConfiguration,
                GridSetConfiguration.class,
                ArcGISCacheGridsetConfiguration.class);

        return super.getConfig(extensions);
    }

    @Override
    @Test
    public void testGetExistingHasGridset() throws Exception {
        Optional<TileLayer> retrieved = getInfo(config, getExistingInfo());
        assertThat(
                retrieved,
                isPresent(hasProperty("gridSubsets", Matchers.containsInAnyOrder("EPSG:3857_testExisting"))));
    }

    @Test
    public void testGetExistingIsArcGisLayer() throws Exception {
        Optional<TileLayer> retrieved = getInfo(config, getExistingInfo());
        assertThat(retrieved.get(), Matchers.instanceOf(ArcGISCacheLayer.class));
    }
}
