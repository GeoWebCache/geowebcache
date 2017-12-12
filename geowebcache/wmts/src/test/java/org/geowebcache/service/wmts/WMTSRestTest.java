/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Sandro Salari, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache.service.wmts;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
@ContextConfiguration(classes = WMTSRestWebConfig.class)
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("test")
public class WMTSRestTest {

    private static Map<String, String> namespaces = new HashMap<String, String>(0);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeClass
    public static void beforeClass() {
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("ows", "http://www.opengis.net/ows/1.1");
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
    }

    @Test
    public void testGetCap() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockMvc.perform(get("/rest/wmts/WMTSCapabilities.xml"))
                .andExpect(content().contentType("text/xml"))
                .andExpect(status().is(200))
                .andExpect(xpath("//wmts:Contents/wmts:Layer", namespaces).nodeCount(1))
                .andExpect(
                        xpath("//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer']", namespaces)
                                .nodeCount(1))
                .andExpect(xpath("//wmts:Contents/wmts:Layer/wmts:Style/ows:Identifier", namespaces)
                        .nodeCount(2))
                .andExpect(xpath("//wmts:Contents/wmts:Layer/wmts:Style[ows:Identifier='style-a']",
                        namespaces).nodeCount(1))
                .andExpect(xpath(
                        "//wmts:Contents/wmts:Layer/wmts:Style[ows:Identifier='style-b']/wmts:LegendURL"
                                + "[@width='125'][@height='130'][@format='image/png'][@minScaleDenominator='5000.0'][@maxScaleDenominator='10000.0']"
                                + "[@xlink:href='https://some-url?some-parameter=value3&another-parameter=value4']",
                        namespaces).nodeCount(1))
                .andExpect(xpath(
                        "//wmts:Contents/wmts:Layer/wmts:ResourceURL[@resourceType='tile']"
                                + "[@format='image/jpeg']"
                                + "[@template='http://localhost/rest/wmts"
                                + "/mockLayer/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/jpeg&time={time}&elevation={elevation}']",
                        namespaces).nodeCount(1))
                .andExpect(xpath(
                        "//wmts:Contents/wmts:Layer/wmts:ResourceURL[@resourceType='FeatureInfo']"
                                + "[@format='text/plain']"
                                + "[@template='http://localhost/rest/wmts"
                                + "/mockLayer/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=text/plain&time={time}&elevation={elevation}']",
                        namespaces).nodeCount(1))
                .andExpect(
                        xpath("//wmts:ServiceMetadataURL[@xlink:href='http://localhost/service/wmts"
                                + "?SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0']", namespaces)
                                        .nodeCount(1))
                .andExpect(
                        xpath("//wmts:ServiceMetadataURL[@xlink:href='http://localhost/rest/wmts"
                                + "/WMTSCapabilities.xml']", namespaces).nodeCount(1));
    }

    @Test
    public void testGetTileWithStyle() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        final File imgPath = new File(getClass().getResource("/image.png").toURI());
        BufferedImage originalImage = ImageIO.read(imgPath);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(originalImage, "png", baos);
        mockMvc.perform(get("/rest/wmts/mockLayer/style-a/EPSG:4326/EPSG:4326:0/0/0?format=image/png"))
                .andExpect(status().isOk())
                .andExpect(header().string("geowebcache-crs", "EPSG:4326"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(baos.toByteArray()));
    }

    @Test
    public void testGetTileWithoutStyle() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockMvc.perform(get("/rest/wmts/mockLayer/EPSG:4326/EPSG:4326:0/0/0?format=image/png"))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetInfoWithStyle() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        final File imgPath = new File(getClass().getResource("/image.png").toURI());
        BufferedImage originalImage = ImageIO.read(imgPath);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(originalImage, "png", baos);
        mockMvc.perform(get("/rest/wmts/mockLayer/style-a/EPSG:4326/EPSG:4326:0/0/0/0/0?format=text/plain"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/plain"));
    }

    @Test
    public void testGetInfoWithoutStyle() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockMvc.perform(get("/rest/wmts/mockLayer/EPSG:4326/EPSG:4326:0/0/0/0/0?format=text/plain"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/plain"));
    }

    @Test
    public void testOWSException() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockMvc.perform(get("/rest/wmts/mockLayer/EPSG:4326/EPSG:4326:0/0/0/0/0?format=text/none"))
                .andDo(print()).andExpect(status().isBadRequest())
                .andExpect(content().contentType("text/xml")).andExpect(
                        xpath("//ows:ExceptionReport/ows:Exception[@exceptionCode='InvalidParameterValue']",
                                namespaces).nodeCount(1));
    }

}
