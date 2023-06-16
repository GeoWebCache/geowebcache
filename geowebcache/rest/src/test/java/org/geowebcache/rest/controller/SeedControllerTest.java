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
 * @author Fernando Mino (Geosolutions), 2019
 */
package org.geowebcache.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.http.HttpServletRequest;
import org.geowebcache.rest.service.FormService;
import org.geowebcache.rest.service.SeedService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@WebAppConfiguration()
@ContextConfiguration({
    "file:../web/src/main/webapp/WEB-INF/geowebcache-rest-context.xml",
    "file:../web/src/main/webapp/WEB-INF/geowebcache-core-context.xml"
})
public class SeedControllerTest {

    @Autowired private WebApplicationContext wac;

    @Mock @Autowired private SeedService seedService;

    @InjectMocks @Autowired SeedController controller;

    @Spy @Autowired FormService formService;

    private MockMvc mockMvc;

    @Before
    public void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        MockitoAnnotations.openMocks(this);
        doCallRealMethod().when(formService).handleFormPost(anyString(), anyMap());
    }

    /** Checks correct media type for RestException response handling. GET method. */
    @Test
    public void testSeedGetContentType() throws Exception {
        mockMvc.perform(get("/rest/seed/{layer}", "xxxp4z85").accept(MediaType.TEXT_HTML))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is4xxClientError());
    }

    /** Checks correct media type for RestException response handling. POST method. */
    @Test
    public void testSeedPostContentType() throws Exception {
        mockMvc.perform(
                        post("/rest/seed/{layer}", "xxxp4z85")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .accept(MediaType.TEXT_HTML))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testSeedLayerNameWithDots() throws Exception {
        String layerName = "test:mock.layer.name";
        // when(formService.handleFormPost(eq(layerName),
        // anyMap())).thenReturn(ResponseEntity.ok(null));
        testGet(layerName);
        testGetJson(layerName);
        testGetXml(layerName);
        testPost(layerName);
    }

    private void testPost(String layerName) throws Exception {
        doReturn(ResponseEntity.ok(null)).when(formService).handleFormPost(anyString(), anyMap());

        mockMvc.perform(
                        post("/rest/seed/{layer}", layerName)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .accept(MediaType.TEXT_XML))
                .andExpect(status().isOk());
        verify(formService).handleFormPost(eq(layerName), anyMap());
    }

    private void testGet(String layerName) throws Exception {
        doReturn(ResponseEntity.ok(null))
                .when(formService)
                .handleGet(any(HttpServletRequest.class), anyString());

        mockMvc.perform(
                        get("/rest/seed/{layer}", layerName)
                                .contentType(MediaType.TEXT_PLAIN)
                                .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());

        verify(formService).handleGet(any(HttpServletRequest.class), eq(layerName));
    }

    private void testGetJson(String layerName) throws Exception {
        doReturn(ResponseEntity.ok(null))
                .when(formService)
                .handleGet(any(HttpServletRequest.class), anyString());

        doReturn(ResponseEntity.ok(null))
                .when(seedService)
                .getRunningLayerTasksXml(endsWith(".json"));

        mockMvc.perform(
                        get("/rest/seed/{layer}.json", layerName)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(controller.seedService).getRunningLayerTasks(eq(layerName));
    }

    private void testGetXml(String layerName) throws Exception {
        doReturn(ResponseEntity.ok(null))
                .when(formService)
                .handleGet(any(HttpServletRequest.class), anyString());

        doReturn(ResponseEntity.ok(null))
                .when(seedService)
                .getRunningLayerTasksXml(endsWith(".xml"));

        mockMvc.perform(
                        get("/rest/seed/{layer}.xml", layerName)
                                .contentType(MediaType.APPLICATION_XML)
                                .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk());

        verify(controller.seedService).getRunningLayerTasks(eq(layerName));
    }
}
