package org.geowebcache.rest.filter;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.*;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.controller.FilterUpdateController;
import org.geowebcache.rest.controller.TileLayerController;
import org.geowebcache.util.NullURLMangler;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.util.LinkedList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FilterUpdateControllerTest {

    private MockMvc mockMvc;

    TileLayerDispatcher tld;

    FilterUpdateController fc;

    @Before
    public void setup() throws GeoWebCacheException {
        GridSetBroker gridSetBroker = new GridSetBroker(false, false);

        BoundingBox extent = new BoundingBox(0, 0, 10E6, 10E6);
        boolean alignTopLeft = false;
        int levels = 10;
        Double metersPerUnit = 1.0;
        double pixelSize = 0.0028;
        int tileWidth = 256;
        int tileHeight = 256;
        boolean yCoordinateFirst = false;
        GridSet gridSet = GridSetFactory.createGridSet("EPSG:3395", SRS.getSRS("EPSG:3395"),
                extent, alignTopLeft, levels, metersPerUnit, pixelSize, tileWidth, tileHeight,
                yCoordinateFirst);
        gridSetBroker.put(gridSet);

        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.initialize(gridSetBroker);
        LinkedList<Configuration> configList = new LinkedList<Configuration>();
        configList.add(xmlConfig);

        tld = new TileLayerDispatcher(gridSetBroker, configList);
        fc = new FilterUpdateController();
        fc.setTileLayerDispatcher(tld);
        this.mockMvc = MockMvcBuilders.standaloneSetup(fc).build();
    }

    private XMLConfiguration loadXMLConfig() {

        InputStream is = XMLConfiguration.class
                .getResourceAsStream(XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE);
        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(is);
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }

    @Test
    public void testPost() throws Exception {

        String filterXml =
                "<wmsRasterFilterUpdate>\n" +
                "    <gridSetId>EPSG:4326</gridSetId>\n" +
                "    <zoomStart>0</zoomStart>\n" +
                "    <zoomStop>6</zoomStop>\n" +
                "</wmsRasterFilterUpdate>";

        this.mockMvc.perform(post("/rest/filter/testWMSRasterFilter/update/xml")
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .contextPath("")
                .content(filterXml))
                .andExpect(status().is2xxSuccessful());

    }
}
