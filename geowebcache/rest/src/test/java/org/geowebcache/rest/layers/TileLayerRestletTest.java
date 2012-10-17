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
 * @author Arne Kepp / The Open Planning Project 2009 
 */
package org.geowebcache.rest.layers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.RestletException;
import org.geowebcache.util.NullURLMangler;
import org.geowebcache.util.ServletUtils;
import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;
import org.w3c.dom.Document;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Most of the work is done by XMLConfig and XStream, so this is fairly short
 */
public class TileLayerRestletTest extends XMLTestCase {
    TileLayerDispatcher tld;

    // For the gets we'll use a shared one
    TileLayerRestlet tlr;

    protected void setUp() throws Exception {
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

        tlr = new TileLayerRestlet();
        tlr.setXMLConfiguration(xmlConfig);
        tlr.setTileLayerDispatcher(tld);
        tlr.setUrlMangler(new NullURLMangler());
    }

    public void testGetXml() throws Exception {
        Representation rep = tlr.doGetInternal("topp:states", "xml");

        String str = rep.getText();

        assertTrue(str.indexOf("<name>topp:states</name>") > 0);
        // TODO This needs to get back in
        // assertTrue(str.indexOf("<double>49.371735</double>") > 0);
        // assertTrue(str.indexOf("<wmsStyles>population</wmsStyles>") > 0);
        assertTrue(str.indexOf("</wmsLayer>") > 0);
        assertTrue(str.indexOf("states2") == -1);
    }

    public void testGetJson() throws Exception {
        Representation rep = tlr.doGetInternal("topp:states2", "json");

        String str = rep.getText();

        assertTrue(str.indexOf(",\"name\":\"topp:states2\",") > 0);
        // TODO this needs to go back in
        // assertTrue(str.indexOf("959189.3312465074]},") > 0);
        assertTrue(str.indexOf("[\"image/png\",\"image/jpeg\"]") > 0);
        assertTrue(str.indexOf("}}") > 0);
    }

    public void testGetInvalid() throws Exception {
        Representation rep = null;
        try {
            rep = tlr.doGetInternal("topp:states", "jpeg");
        } catch (RestletException re) {
            // Format should be invalid
            assertTrue(re.getRepresentation().getText().indexOf("format") > 0);
        }
        assertTrue(rep == null);
    }

    public void testGetList() throws Exception {

        final String rootPath = "http://my.gwc.org";
        final String contextPath = "/rest";
        Representation rep = tlr.listLayers("xml", rootPath, contextPath);
        assertNotNull(rep);
        assertEquals(CharacterSet.UTF_8, rep.getCharacterSet());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        rep.write(output);

        // System.err.println(output.toString());

        Document dom = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(output.toByteArray()));

        List<String> layerNames = Lists.newArrayList(tld.getLayerNames());
        Collections.sort(layerNames);
        assertXpathExists("/layers", dom);

        NamespaceContext ctx = new SimpleNamespaceContext(ImmutableMap.of("atom",
                "http://www.w3.org/2005/Atom"));
        XpathEngine xpathEngine = XMLUnit.newXpathEngine();
        xpathEngine.setNamespaceContext(ctx);

        for (int i = 0; i < layerNames.size(); i++) {
            int xpathIndex = i + 1;

            String layerName = layerNames.get(i);
            String xpath = "/layers/layer[" + xpathIndex + "]/name";
            assertXpathEvaluatesTo(layerName, xpath, dom);

            String href = rootPath + contextPath + "/layers/" + ServletUtils.URLEncode(layerName) + ".xml";
            xpath = "/layers/layer[" + xpathIndex + "]/link/@href";
            String actual = xpathEngine.evaluate(xpath, dom);
            // System.err.println("-------- " + actual);
            assertEquals(href, actual);
        }
    }

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

        Request request;
        Representation entity;
        Response response;

        request = new Request();
        request.setMethod(Method.PUT);
        request.getAttributes().put("layer", "newLayer1");
        request.getAttributes().put("extension", "xml");
        entity = new StringRepresentation(layerXml, MediaType.TEXT_XML);
        request.setEntity(entity);
        response = new Response(request);

        tlr.handle(request, response);
        assertEquals(Status.SUCCESS_OK, response.getStatus());

        TileLayer tileLayer1 = tld.getTileLayer("newLayer1");
        assertEquals(1, tileLayer1.getGridSubsets().size());
        assertNotNull(tileLayer1.getGridSubset("EPSG:3395"));

        request = new Request();
        request.setMethod(Method.PUT);
        request.getAttributes().put("layer", "newLayer2");
        request.getAttributes().put("extension", "xml");
        entity = new StringRepresentation(layerXml2, MediaType.TEXT_XML);
        request.setEntity(entity);
        response = new Response(request);

        tlr.handle(request, response);
        assertEquals(Status.SUCCESS_OK, response.getStatus());

        TileLayer tileLayer2 = tld.getTileLayer("newLayer2");
        assertEquals(1, tileLayer2.getGridSubsets().size());
        assertNotNull(tileLayer2.getGridSubset("EPSG:3395"));

        tileLayer1 = tld.getTileLayer("newLayer1");
        assertNotNull(tileLayer1.getGridSubset("EPSG:3395"));
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
