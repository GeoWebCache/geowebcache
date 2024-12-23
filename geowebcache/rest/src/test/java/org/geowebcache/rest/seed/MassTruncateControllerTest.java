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
 * @author David Vick, Boundless, 2017
 */
package org.geowebcache.rest.seed;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.MockConfigurationResourceProvider;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.controller.MassTruncateController;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.StorageBroker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"file*:/webapp/WEB-INF/web.xml", "file*:/webapp/WEB-INF/geowebcache-servlet.xml"})
public class MassTruncateControllerTest {
    private MockMvc mockMvc;

    MassTruncateController mtc;

    @Before
    public void setup() throws GeoWebCacheException {
        GridSetBroker gridSetBroker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));
        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.setGridSetBroker(gridSetBroker);
        xmlConfig.afterPropertiesSet();

        mtc = new MassTruncateController(null);

        this.mockMvc = MockMvcBuilders.standaloneSetup(mtc).build();
    }

    @Test
    public void testTruncateLayer() throws Exception {
        Set<String> layer1GridSet = new HashSet<>(Arrays.asList("test_grid1", "test_grid2"));
        String layerName = "test";
        TileLayer tl1 = createMock(TileLayer.class);
        expect(tl1.getGridSubsets()).andReturn(layer1GridSet).anyTimes();
        String requestBody = "<truncateLayer><layerName>" + layerName + "</layerName></truncateLayer>";

        TileBreeder tb = createMock(TileBreeder.class);
        expect(tb.findTileLayer(layerName)).andReturn(tl1);

        StorageBroker sb = createMock(StorageBroker.class);
        for (String grid : layer1GridSet)
            expect(sb.deleteByGridSetId(layerName, grid)).andReturn(true);
        replay(tl1);
        replay(sb);
        replay(tb);

        mtc.setStorageBroker(sb);
        mtc.setTileBreeder(tb);

        this.mockMvc
                .perform(post("/rest/masstruncate")
                        .contentType(MediaType.TEXT_XML)
                        .content(requestBody)
                        .contextPath(""))
                .andExpect(status().is2xxSuccessful());
        verify(sb);
    }

    @Test
    public void testTruncateLayerTwice() throws Exception {
        Set<String> layer1GridSet = new HashSet<>(Arrays.asList("test_grid1", "test_grid2"));
        String layerName = "test";
        TileLayer tileLayer = createMock(TileLayer.class);
        expect(tileLayer.getName()).andReturn(layerName).anyTimes();
        expect(tileLayer.getGridSubsets()).andReturn(layer1GridSet);
        expect(tileLayer.getGridSubsets()).andReturn(new HashSet<>());
        replay(tileLayer);
        String requestBody = "<truncateLayer><layerName>" + layerName + "</layerName></truncateLayer>";

        StorageBroker sb = createMock(StorageBroker.class);
        for (String grid : layer1GridSet)
            expect(sb.deleteByGridSetId(layerName, grid)).andReturn(true);
        replay(sb);

        TileBreeder tb = createMock(TileBreeder.class);
        expect(tb.findTileLayer(layerName)).andReturn(tileLayer).times(2); // just do not throw an
        replay(tb);

        mtc.setStorageBroker(sb);
        mtc.setTileBreeder(tb);

        // first run, it will get an ok
        this.mockMvc
                .perform(post("/rest/masstruncate")
                        .contentType(MediaType.TEXT_XML)
                        .content(requestBody)
                        .contextPath(""))
                .andExpect(status().is2xxSuccessful());
        // second run, will get a falso and will check the breeded for layer existence
        this.mockMvc
                .perform(post("/rest/masstruncate")
                        .contentType(MediaType.TEXT_XML)
                        .content(requestBody)
                        .contextPath(""))
                .andExpect(status().is2xxSuccessful());
        verify(sb);
    }

    @Test
    public void testTruncateNonExistingLayer() throws Exception {
        String layerName = "test";
        String requestBody = "<truncateLayer><layerName>" + layerName + "</layerName></truncateLayer>";

        StorageBroker sb = createMock(StorageBroker.class);
        replay(sb);

        TileBreeder tb = createMock(TileBreeder.class);
        expect(tb.findTileLayer(layerName)).andThrow(new GeoWebCacheException("Layer not found"));
        replay(tb);

        mtc.setStorageBroker(sb);
        mtc.setTileBreeder(tb);

        // first run, it will get a bad request
        this.mockMvc
                .perform(post("/rest/masstruncate")
                        .contentType(MediaType.TEXT_XML)
                        .content(requestBody)
                        .contextPath(""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Could not find layer test")));
        verify(sb);
    }

    @Test
    public void testGetMassTruncate() throws Exception {
        MvcResult result = this.mockMvc
                .perform(get("/rest/masstruncate")
                        .contentType(MediaType.APPLICATION_ATOM_XML)
                        .contextPath(""))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
    }

    @Test
    public void testTruncateAllLayers() throws Exception {
        Set<String> layer1GridSet = new HashSet<>(Arrays.asList("test1_grid1", "test1_grid2"));
        Set<String> layer2GridSet = new HashSet<>(Arrays.asList("test22_grid1", "test22_grid2"));

        String layerName = "test11";
        String layerName2 = "test22";
        // setting up calls
        TileLayer tl1 = createMock(TileLayer.class);
        TileLayer tl2 = createMock(TileLayer.class);

        expect(tl1.getName()).andReturn(layerName).anyTimes();
        expect(tl1.getGridSubsets()).andReturn(layer1GridSet).anyTimes();

        expect(tl2.getName()).andReturn(layerName2).anyTimes();
        expect(tl2.getGridSubsets()).andReturn(layer2GridSet).anyTimes();

        replay(tl1);
        replay(tl2);

        ArrayList<TileLayer> mockList = new ArrayList<>();
        mockList.add(tl1);
        mockList.add(tl2);

        String requestBody = "<truncateAll></truncateAll>";

        TileBreeder tb = createMock(TileBreeder.class);
        expect(tb.getLayers()).andReturn(mockList);

        StorageBroker sb = createMock(StorageBroker.class);

        for (String grid : layer1GridSet)
            expect(sb.deleteByGridSetId(layerName, grid)).andReturn(true);

        for (String grid : layer2GridSet)
            expect(sb.deleteByGridSetId(layerName2, grid)).andReturn(true);

        replay(sb);
        replay(tb);

        mtc.setStorageBroker(sb);
        mtc.setTileBreeder(tb);

        this.mockMvc
                .perform(post("/rest/masstruncate")
                        .contentType(MediaType.TEXT_XML)
                        .content(requestBody)
                        .contextPath(""))
                .andExpect(status().is2xxSuccessful());
        verify(sb);
    }

    @Test
    public void testTruncateAllLayersFromHTMLForm() throws Exception {
        Set<String> layer1GridSet = new HashSet<>(Arrays.asList("test1_grid1", "test1_grid2"));
        Set<String> layer2GridSet = new HashSet<>(Arrays.asList("test2_grid1", "test2_grid2"));

        String layerName = "test1";
        String layerName2 = "test2";
        // setting up calls
        TileLayer tl1 = createMock(TileLayer.class);
        TileLayer tl2 = createMock(TileLayer.class);

        expect(tl1.getName()).andReturn(layerName).anyTimes();
        expect(tl1.getGridSubsets()).andReturn(layer1GridSet).anyTimes();

        expect(tl2.getName()).andReturn(layerName2).anyTimes();
        expect(tl2.getGridSubsets()).andReturn(layer2GridSet).anyTimes();

        replay(tl1);
        replay(tl2);

        ArrayList<TileLayer> mockList = new ArrayList<>();
        mockList.add(tl1);
        mockList.add(tl2);

        TileBreeder tb = createMock(TileBreeder.class);
        expect(tb.getLayers()).andReturn(mockList);

        StorageBroker sb = createMock(StorageBroker.class);
        for (String grid : layer1GridSet)
            expect(sb.deleteByGridSetId(layerName, grid)).andReturn(true);

        for (String grid : layer2GridSet)
            expect(sb.deleteByGridSetId(layerName2, grid)).andReturn(true);

        replay(sb);
        replay(tb);

        mtc.setStorageBroker(sb);
        mtc.setTileBreeder(tb);
        String requestBody = "<truncateAll>=</truncateAll>";

        MvcResult result = this.mockMvc
                .perform(post("/rest/masstruncate")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(requestBody)
                        .contextPath(""))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("Truncated Layers:test1,test2"));
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
}
