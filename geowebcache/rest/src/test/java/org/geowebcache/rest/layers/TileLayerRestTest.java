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
 * @author David Vick, Boundless, 2017
 */

package org.geowebcache.rest.layers;

import org.easymock.EasyMock;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.*;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.controller.TileLayerController;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.NullURLMangler;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.util.LinkedList;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
        "file*:/webapp/WEB-INF/web.xml",
        "file*:/webapp/WEB-INF/geowebcache-servlet.xml"
})
public class TileLayerRestTest {

    private MockMvc mockMvc;

    TileLayerDispatcher tld;

    TileLayerController tlc;

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

        tlc = new TileLayerController();
        tlc.setXMLConfiguration(xmlConfig);
        tlc.setTileLayerDispatcher(tld);
        tlc.setUrlMangler(new NullURLMangler());
        this.mockMvc = MockMvcBuilders.standaloneSetup(tlc).build();
    }

    @Test
    public void testGetXml() {
        ResponseEntity rep = tlc.doGetInternal("topp:states", "xml");

        String str = rep.getBody().toString();

        assertTrue(str.indexOf("<name>topp:states</name>") > 0);
        // TODO This needs to get back in
        // assertTrue(str.indexOf("<double>49.371735</double>") > 0);
        // assertTrue(str.indexOf("<wmsStyles>population</wmsStyles>") > 0);
        assertTrue(str.indexOf("</wmsLayer>") > 0);
        assertTrue(str.indexOf("states2") == -1);
    }

    @Test
    public void testGetJson() {
        ResponseEntity rep = tlc.doGetInternal("topp:states2", "json");

        String str = rep.getBody().toString();

        assertTrue(str.indexOf(",\"name\":\"topp:states2\",") > 0);
        // TODO this needs to go back in
        // assertTrue(str.indexOf("959189.3312465074]},") > 0);
        assertTrue(str.indexOf("[\"image/png\",\"image/jpeg\"]") > 0);
        assertTrue(str.indexOf("}}") > 0);
    }

    @Test
    public void testGetInvalid() {
        ResponseEntity rep = null;
        try {
            rep = tlc.doGetInternal("topp:states", "jpeg");
        } catch (RestException re) {
            // Format should be invalid
            assertTrue(re.getStatus().is4xxClientError());
        }
        assertTrue(rep == null);
    }

    @Test
    public void testGetList() throws Exception {
        this.mockMvc.perform(get("/rest/layers.xml")
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .contextPath(""))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    public void testPut() throws Exception {
        String layerXml = "<wmsLayer>" + //
                "  <name>newLayer1</name>" + //
                "  <mimeFormats>" + //
                "    <string>image/png</string>" + //
                "  </mimeFormats>" + //
                "  <gridSubsets>" + //
                "    <gridSubset>" + //
                "      <gridSetName>EPSG:3395</gridSetName>" + //
                "    </gridSubset>" + //
                "  </gridSubsets>" + //
                "  <wmsUrl>" + //
                "    <string>http://localhost:8080/geoserver/wms</string>" + //
                "  </wmsUrl>" + //
                "  <wmsLayers>topp:states</wmsLayers>" + //
                "</wmsLayer>";

        String layerXml2 = "<wmsLayer>" + //
                "  <name>newLayer2</name>" + //
                "  <mimeFormats>" + //
                "    <string>image/png</string>" + //
                "  </mimeFormats>" + //
                "  <gridSubsets>" + //
                "    <gridSubset>" + //
                "      <gridSetName>EPSG:3395</gridSetName>" + //
                "    </gridSubset>" + //
                "  </gridSubsets>" + //
                "  <wmsUrl>" + //
                "    <string>http://localhost:8080/geoserver/wms</string>" + //
                "  </wmsUrl>" + //
                "  <wmsLayers>topp:states</wmsLayers>" + //
                "</wmsLayer>";

        this.mockMvc.perform(put("/rest/layers/newLayer1.xml")
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .contextPath("")
                .content(layerXml))
                .andExpect(status().is2xxSuccessful());

        TileLayer tileLayer1 = tld.getTileLayer("newLayer1");
        assertEquals(1, tileLayer1.getGridSubsets().size());
        assertNotNull(tileLayer1.getGridSubset("EPSG:3395"));

        this.mockMvc.perform(put("/rest/layers/newLayer2.xml")
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .contextPath("")
                .content(layerXml2))
                .andExpect(status().is2xxSuccessful());

        TileLayer tileLayer2 = tld.getTileLayer("newLayer2");
        assertEquals(1, tileLayer2.getGridSubsets().size());
        assertNotNull(tileLayer2.getGridSubset("EPSG:3395"));

        tileLayer1 = tld.getTileLayer("newLayer1");
        assertNotNull(tileLayer1.getGridSubset("EPSG:3395"));
    }

    /**
     * Test deletion of topp:states layer.
     */
    @Test
    public void testDelete() throws Exception {
        String layerName = "topp:states";
        assertNotNull("Missing test layer", tld.getTileLayer(layerName));
        StorageBroker storageBroker = EasyMock
                .createMock(StorageBroker.class);
        EasyMock.expect(storageBroker.delete(EasyMock.eq(layerName))).andReturn(true);
        EasyMock.replay(storageBroker);
        tlc.setStorageBroker(storageBroker);

        this.mockMvc.perform(delete("/rest/layers/" + layerName)
                .contextPath(""))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().string(containsString("topp:states deleted")));

        try {
            tld.getTileLayer(layerName);
            fail("Test layer not deleted");
        } catch (GeoWebCacheException e) {
            // success
        }
        EasyMock.verify(storageBroker);
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
}
