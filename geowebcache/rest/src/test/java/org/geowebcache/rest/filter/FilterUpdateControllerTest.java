package org.geowebcache.rest.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.geowebcache.rest.controller.FilterUpdateController;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class FilterUpdateControllerTest {

    private MockMvc mockMvc;

    TileLayerDispatcher tld;

    FilterUpdateController fc;

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
        GridSet gridSet = GridSetFactory.createGridSet(
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

        GridSetBroker gridSetBroker = new GridSetBroker(MockGridSetConfiguration.withDefaults(gridSet));

        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.setGridSetBroker(gridSetBroker);
        xmlConfig.afterPropertiesSet();
        LinkedList<TileLayerConfiguration> configList = new LinkedList<>();
        configList.add(xmlConfig);

        tld = new TileLayerDispatcher(gridSetBroker, configList, null);
        fc = new FilterUpdateController();
        fc.setTileLayerDispatcher(tld);
        this.mockMvc = MockMvcBuilders.standaloneSetup(fc).build();
    }

    private XMLConfiguration loadXMLConfig() {

        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(
                    null,
                    new MockConfigurationResourceProvider(() -> XMLConfiguration.class.getResourceAsStream(
                            XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE)));
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }

    @Test
    public void testPost() throws Exception {

        String filterXml =
                """
                <wmsRasterFilterUpdate>
                    <gridSetId>EPSG:4326</gridSetId>
                    <zoomStart>0</zoomStart>
                    <zoomStop>6</zoomStop>
                </wmsRasterFilterUpdate>""";

        this.mockMvc
                .perform(post("/rest/filter/testWMSRasterFilter/update/xml")
                        .contentType(MediaType.APPLICATION_ATOM_XML)
                        .contextPath("")
                        .content(filterXml))
                .andExpect(status().is2xxSuccessful());
    }
}
