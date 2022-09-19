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
 * @author David Vick, Boundless, 2017
 */
package org.geowebcache.rest.bounds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedList;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.MockConfigurationResourceProvider;
import org.geowebcache.config.MockGridSetConfiguration;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.controller.BoundsController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
    "file*:/webapp/WEB-INF/web.xml",
    "file*:/webapp/WEB-INF/geowebcache-servlet.xml"
})
public class BoundsControllerTest {
    private MockMvc mockMvc;

    BoundsController bc;

    TileLayerDispatcher tld;

    @Before
    public void setup() throws GeoWebCacheException {

        BoundingBox extent = new BoundingBox(0, 0, 10E6, 10E6);
        boolean alignTopLeft = false;
        int levels = 10;
        Double metersPerUnit = 1.0;
        double pixelSize = 0.0028;
        int tileWidth = 256;
        int tileHeight = 256;
        boolean yCoordinateFirst = false;
        GridSet gridSet =
                GridSetFactory.createGridSet(
                        "EPSG:3395",
                        SRS.getSRS("EPSG:3395"),
                        extent,
                        alignTopLeft,
                        levels,
                        metersPerUnit,
                        pixelSize,
                        tileWidth,
                        tileHeight,
                        yCoordinateFirst);
        GridSetBroker gridSetBroker =
                new GridSetBroker(MockGridSetConfiguration.withDefaults(gridSet));

        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.setGridSetBroker(gridSetBroker);
        xmlConfig.afterPropertiesSet();
        LinkedList<TileLayerConfiguration> configList = new LinkedList<>();
        configList.add(xmlConfig);

        tld = new TileLayerDispatcher(gridSetBroker, configList, null);
        bc = new BoundsController();
        bc.setTileLayerDispatcher(tld);
        this.mockMvc = MockMvcBuilders.standaloneSetup(bc).build();
    }

    @Test
    public void testBoundsGetBadSrs() throws Exception {
        this.mockMvc
                .perform(get("/rest/bounds/topp:states/4326/java"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testBoundsGetGoodSrs() throws Exception {
        this.mockMvc
                .perform(get("/rest/bounds/topp:states/EPSG:900913/java"))
                .andExpect(status().is2xxSuccessful());
    }

    private XMLConfiguration loadXMLConfig() {

        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig =
                    new XMLConfiguration(
                            null,
                            new MockConfigurationResourceProvider(
                                    () ->
                                            XMLConfiguration.class.getResourceAsStream(
                                                    XMLConfigurationBackwardsCompatibilityTest
                                                            .GWC_125_CONFIG_FILE)));
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }
}
