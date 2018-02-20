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

package org.geowebcache.rest.seed;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.MockConfigurationResourceProvider;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.controller.MassTruncateController;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.StorageBroker;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.util.Arrays;

import static org.easymock.EasyMock.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
        "file*:/webapp/WEB-INF/web.xml",
        "file*:/webapp/WEB-INF/geowebcache-servlet.xml"
})
public class MassTruncateControllerTest {
    private MockMvc mockMvc;

    MassTruncateController mtc;

    @Before
    public void setup() throws GeoWebCacheException {
        GridSetBroker gridSetBroker = new GridSetBroker(false, false);
        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.setGridSetBroker(gridSetBroker);
        xmlConfig.afterPropertiesSet();

        mtc = new MassTruncateController(null);

        this.mockMvc = MockMvcBuilders.standaloneSetup(mtc).build();
    }

    @Test
    public void testTruncateLayer() throws Exception {
        String layerName = "test";
        String requestBody = "<truncateLayer><layerName>"+layerName+"</layerName></truncateLayer>";

        StorageBroker sb = createMock(StorageBroker.class);
        expect(sb.delete(eq(layerName))).andReturn(true);
        replay(sb);

        mtc.setStorageBroker(sb);

        this.mockMvc.perform(post("/rest/masstruncate")
                .contentType(MediaType.TEXT_XML)
                .content(requestBody)
                .contextPath("")).andExpect(status().is2xxSuccessful());
        verify(sb);
    }

    @Test
    public void testTruncateLayerTwice() throws Exception {
        String layerName = "test";
        String requestBody = "<truncateLayer><layerName>"+layerName+"</layerName></truncateLayer>";

        StorageBroker sb = createMock(StorageBroker.class);
        expect(sb.delete(eq(layerName))).andReturn(true);
        expect(sb.delete(eq(layerName))).andReturn(false);
        replay(sb);
        
        TileBreeder tb = createMock(TileBreeder.class);
        TileLayer tileLayer = createMock(TileLayer.class);
        expect(tb.findTileLayer(layerName)).andReturn(tileLayer); // just do not throw an 
        replay(tb);

        mtc.setStorageBroker(sb);
        mtc.setTileBreeder(tb);

        // first run, it will get an ok
        this.mockMvc.perform(post("/rest/masstruncate")
                .contentType(MediaType.TEXT_XML)
                .content(requestBody)
                .contextPath("")).andExpect(status().is2xxSuccessful());
        // second run, will get a falso and will check the breeded for layer existence
        this.mockMvc.perform(post("/rest/masstruncate")
                .contentType(MediaType.TEXT_XML)
                .content(requestBody)
                .contextPath("")).andExpect(status().is2xxSuccessful());
        verify(sb);
    }

    @Test
    public void testTruncateNonExistingLayer() throws Exception {
        String layerName = "test";
        String requestBody = "<truncateLayer><layerName>"+layerName+"</layerName></truncateLayer>";

        StorageBroker sb = createMock(StorageBroker.class);
        expect(sb.delete(eq(layerName))).andReturn(false);
        replay(sb);

        TileBreeder tb = createMock(TileBreeder.class);
        expect(tb.findTileLayer(layerName)).andThrow(new GeoWebCacheException("Layer not found")); 
        replay(tb);

        mtc.setStorageBroker(sb);
        mtc.setTileBreeder(tb);

        // first run, it will get a bad request
        this.mockMvc.perform(post("/rest/masstruncate")
                .contentType(MediaType.TEXT_XML)
                .content(requestBody)
                .contextPath(""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Could not find layer test")));
        verify(sb);
    }

    @Test
    public void testGetMassTruncate() throws Exception {
        MvcResult result = this.mockMvc.perform(get("/rest/masstruncate")
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .contextPath("")).andReturn();

        assertEquals(200, result.getResponse().getStatus());

        System.out.print(result);
    }

    private XMLConfiguration loadXMLConfig() {

        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(null, new MockConfigurationResourceProvider(
                    ()->XMLConfiguration.class.getResourceAsStream(
                            XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE)));
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }
}
