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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import static org.geowebcache.util.TestUtils.isPresent;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class XMLConfigurationLayerConformanceTest extends LayerConfigurationTest {

    public @Rule MockWepAppContextRule extensions = new MockWepAppContextRule();
    public @Rule MockWepAppContextRule extensions2 = new MockWepAppContextRule(false);

    @Override
    protected TileLayer getGoodInfo(String id, int rand) {
        WMSLayer layer =
                new WMSLayer(
                        id,
                        new String[] {"http://example.com/"},
                        null,
                        Integer.toString(rand),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null);
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
            public void seedTile(ConveyorTile tile, boolean tryCache)
                    throws GeoWebCacheException, IOException {
                // TODO Auto-generated method stub

            }

            @Override
            public ConveyorTile doNonMetatilingRequest(ConveyorTile tile)
                    throws GeoWebCacheException {
                // TODO Auto-generated method stub
                return null;
            }
        };
    }

    @Rule public TemporaryFolder temp = new TemporaryFolder();
    protected File configDir;
    protected File configFile;

    @Override
    protected TileLayerConfiguration getConfig() throws Exception {
        makeConfigFile();
        return getConfig(extensions);
    }

    @Override
    protected TileLayerConfiguration getSecondConfig() throws Exception {
        return getConfig(extensions2);
    }

    TestXMLConfigurationSource configSource = new TestXMLConfigurationSource();

    protected TileLayerConfiguration getConfig(MockWepAppContextRule extensions) throws Exception {
        return configSource.create(extensions, configDir);
    }

    protected void makeConfigFile() throws Exception {
        if (configFile == null) {
            configDir = temp.getRoot();
            configFile = temp.newFile("geowebcache.xml");

            URL source = XMLConfiguration.class.getResource("geowebcache_1120.xml");
            FileUtils.copyURLToFile(source, configFile);
        }
    }

    @Override
    protected Matcher<TileLayer> infoEquals(TileLayer expected) {
        return new CustomMatcher<TileLayer>(
                "Layer matching "
                        + expected.getId()
                        + " with "
                        + ((WMSLayer) expected).getWmsLayers()) {

            @Override
            public boolean matches(Object item) {
                return item instanceof WMSLayer
                        && ((WMSLayer) item).getId().equals(expected.getId())
                        && ((WMSLayer) item)
                                .getWmsLayers()
                                .equals(((WMSLayer) expected).getWmsLayers());
            }
        };
    }

    @Override
    protected Matcher<TileLayer> infoEquals(int expected) {
        return new CustomMatcher<TileLayer>("Layer with value" + expected) {

            @Override
            public boolean matches(Object item) {
                return item instanceof WMSLayer
                        && ((WMSLayer) item).getWmsLayers().equals(expected);
            }
        };
    }

    @Override
    protected String getExistingInfo() {
        return "topp:states";
    }

    @Override
    public void failNextRead() {
        configSource.setFailNextRead(true);
    }

    @Override
    public void failNextWrite() {
        configSource.setFailNextWrite(true);
    }

    @Override
    protected void renameInfo(TileLayerConfiguration config, String name1, String name2)
            throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void doModifyInfo(TileLayer info, int rand) throws Exception {
        ((WMSLayer) info).setWmsLayers(Integer.toString(rand));
    }

    @Override
    @Ignore // TODO Need to implement a clone/deep copy/modification proxy to make this safe.
    @Test
    public void testModifyCallRequiredToChangeInfoFromGetInfo() throws Exception {
        super.testModifyCallRequiredToChangeInfoFromGetInfo();
    }

    @Override
    @Ignore // TODO Need to implement a clone/deep copy/modification proxy to make this safe.
    @Test
    public void testModifyCallRequiredToChangeInfoFromGetInfos() throws Exception {
        super.testModifyCallRequiredToChangeInfoFromGetInfos();
    }

    @Override
    @Ignore // TODO Need to implement a clone/deep copy/modification proxy to make this safe.
    @Test
    public void testModifyCallRequiredToChangeExistingInfoFromGetInfo() throws Exception {
        super.testModifyCallRequiredToChangeExistingInfoFromGetInfo();
    }

    @Override
    @Ignore // TODO Need to implement a clone/deep copy/modification proxy to make this safe.
    @Test
    public void testModifyCallRequiredToChangeExistingInfoFromGetInfos() throws Exception {
        super.testModifyCallRequiredToChangeExistingInfoFromGetInfos();
    }

    @Test
    public void testGetExistingHasGridset() throws Exception {
        Optional<TileLayer> retrieved = getInfo(config, getExistingInfo());
        assertThat(
                retrieved,
                isPresent(
                        hasProperty(
                                "gridSubsets",
                                Matchers.containsInAnyOrder("EPSG:4326", "EPSG:2163"))));
    }
}
