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
 * @author Kevin Smith, Boundless
 * @author David Vick, Boundless, 2017
 */

package org.geowebcache.jetty;

import junit.framework.TestCase;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.custommonkey.xmlunit.XMLUnit;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.Arrays;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Integration test for the REST API in a full GWC instance *
 *
 * @author Kevin Smith, Boundless
 * @author David Vick, Boundless
 * @author Torben Barsballe, Boundless
 */
public class RestIntegrationTest {

    @ClassRule
    static public JettyRule jetty = new JettyRule();
    
    @Rule
    public HttpClientRule anonymous = HttpClientRule.anonymous();
    @Rule
    public HttpClientRule admin = new HttpClientRule("geowebcache", "secured", "admin");
    @Rule
    public HttpClientRule badPassword = new HttpClientRule("geowebcache", "notTheRightPassword", "badPassword");
    @Rule
    public HttpClientRule notAUser = new HttpClientRule("notARealUser", "somePassword", "notAUser");
    
    
    private SimpleNamespaceContext nsContext;
    
    @Before
    public void setUp() throws Exception {
        nsContext = new SimpleNamespaceContext();
        nsContext.bindNamespaceUri("atom", "http://www.w3.org/2005/Atom");
        nsContext.bindNamespaceUri("wmts", "http://www.opengis.net/wmts/1.0");
        nsContext.bindNamespaceUri("ows", "http://www.opengis.net/ows/1.1");

        //Reset Server configuration
        final String globalUpdate =
                "<global>\n" +
                "  <serviceInformation>\n" +
                "    <title>GeoWebCache</title>\n" +
                "    <description>GeoWebCache is an advanced tile cache for WMS servers. It supports a large variety of protocols and\n" +
                "      formats, including WMS-C, WMTS, KML, Google Maps and Virtual Earth.</description>\n" +
                "    <keywords>\n" +
                "      <string>WMS</string>\n" +
                "      <string>WFS</string>\n" +
                "      <string>WMTS</string>\n" +
                "      <string>GEOWEBCACHE</string>\n" +
                "    </keywords>\n" +
                "    <serviceProvider>\n" +
                "      <providerName>John Smith inc.</providerName>\n" +
                "      <providerSite>http://www.example.com/</providerSite>\n" +
                "      <serviceContact>\n" +
                "        <individualName>John Smith</individualName>\n" +
                "        <positionName>Geospatial Expert</positionName>\n" +
                "        <addressType>Work</addressType>\n" +
                "        <addressStreet>1 Bumpy St.</addressStreet>\n" +
                "        <addressCity>Hobart</addressCity>\n" +
                "        <addressAdministrativeArea>TAS</addressAdministrativeArea>\n" +
                "        <addressPostalCode>7005</addressPostalCode>\n" +
                "        <addressCountry>Australia</addressCountry>\n" +
                "        <phoneNumber>+61 3 0000 0000</phoneNumber>\n" +
                "        <faxNumber>+61 3 0000 0001</faxNumber>\n" +
                "        <addressEmail>john.smith@example.com</addressEmail>\n" +
                "      </serviceContact>\n" +
                "    </serviceProvider>\n" +
                "    <fees>NONE</fees>\n" +
                "    <accessConstraints>NONE</accessConstraints>\n" +
                "  </serviceInformation>\n" +
                "  <runtimeStatsEnabled>true</runtimeStatsEnabled>\n" +
                "  <wmtsCiteCompliant>false</wmtsCiteCompliant>\n" +
                "  <backendTimeout>120</backendTimeout>\n" +
                "</global>";

        CloseableHttpResponse  response = handlePut(URI.create("/geowebcache/rest/global"), admin.getClient(), globalUpdate);
        assertEquals(200, response.getStatusLine().getStatusCode());
        response.close();
    }
    
    Matcher<Node> hasXPath(final String xpathExpr, final Matcher<String> matcher) {
        return Matchers.hasXPath(xpathExpr, nsContext, matcher);
        
    }
    Matcher<Node> hasXPath(final String xpathExpr) {
        return Matchers.hasXPath(xpathExpr, nsContext);
    }

    /* General REST API Tests ********************************************************************/

    @Test
    public void testGetLogo() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/web/geowebcache_logo.png"),
                anonymous.getClient());
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testGetCss() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/web/gwc.css"),
                anonymous.getClient());
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testGetBadWebResource() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/web/thisDoesNotExist"),
                anonymous.getClient());
        TestCase.assertEquals(404, response.getStatusLine().getStatusCode());
    }

    /* ServerController Integration Tests ********************************************************/

    @Test
    public void testGetGlobal() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/global.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("/global/serviceInformation/title", equalTo("GeoWebCache")));
        assertThat(doc, hasXPath("normalize-space(/global/serviceInformation/description)", equalTo(
                "GeoWebCache is an advanced tile cache for WMS servers. It supports a large variety of " +
                        "protocols and formats, including WMS-C, WMTS, KML, Google Maps and Virtual Earth.")));
        assertThat(doc, hasXPath("count(/global/serviceInformation/keywords/string)", equalTo("4")));
        assertThat(doc, hasXPath("/global/serviceInformation/serviceProvider/providerName", equalTo("John Smith inc.")));
        assertThat(doc, hasXPath("/global/serviceInformation/serviceProvider/serviceContact/individualName", equalTo("John Smith")));
        assertThat(doc, hasXPath("/global/runtimeStatsEnabled", equalTo("true")));
        assertThat(doc, hasXPath("/global/wmtsCiteCompliant", equalTo("false")));
        assertThat(doc, hasXPath("/global/backendTimeout", equalTo("120")));
    }

    @Test
    public void testPutGlobal() throws Exception {
        final String globalUpdate =
                "<global>\n" +
                "  <serviceInformation>\n" +
                "    <title>GeoWebCache</title>\n" +
                "    <description>GeoWebCache is an advanced tile cache for WMS servers. It supports a large variety of protocols and\n" +
                "      formats, including WMS-C, WMTS, KML, Google Maps and Virtual Earth.</description>\n" +
                "    <keywords>\n" +
                "      <string>WMS</string>\n" +
                "      <string>WMTS</string>\n" +
                "      <string>GEOWEBCACHE</string>\n" +
                "    </keywords>\n" +
                "    <serviceProvider>\n" +
                "      <providerName>Jane Doe inc.</providerName>\n" +
                "      <providerSite>http://www.example.com/</providerSite>\n" +
                "      <serviceContact>\n" +
                "        <individualName>Jane Doe</individualName>\n" +
                "        <positionName>Geospatial Expert</positionName>\n" +
                "        <addressType>Work</addressType>\n" +
                "        <addressStreet>1 Bumpy St.</addressStreet>\n" +
                "        <addressCity>Hobart</addressCity>\n" +
                "        <addressAdministrativeArea>TAS</addressAdministrativeArea>\n" +
                "        <addressPostalCode>7005</addressPostalCode>\n" +
                "        <addressCountry>Australia</addressCountry>\n" +
                "        <phoneNumber>+61 3 0000 0000</phoneNumber>\n" +
                "        <faxNumber>+61 3 0000 0001</faxNumber>\n" +
                "        <addressEmail>jane.doe@example.com</addressEmail>\n" +
                "      </serviceContact>\n" +
                "    </serviceProvider>\n" +
                "    <fees>NONE</fees>\n" +
                "    <accessConstraints>NONE</accessConstraints>\n" +
                "  </serviceInformation>\n" +
                "  <runtimeStatsEnabled>false</runtimeStatsEnabled>\n" +
                "  <wmtsCiteCompliant>false</wmtsCiteCompliant>\n" +
                "  <backendTimeout>120</backendTimeout>\n" +
                "</global>";

        testGetGlobal();

        CloseableHttpResponse  response = handlePut(URI.create("/geowebcache/rest/global"), admin.getClient(), globalUpdate);
        assertEquals(200, response.getStatusLine().getStatusCode());
        response.close();

        response = handleGet(URI.create("/geowebcache/rest/global.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("/global/serviceInformation/title", equalTo("GeoWebCache")));
        assertThat(doc, hasXPath("normalize-space(/global/serviceInformation/description)", equalTo(
                "GeoWebCache is an advanced tile cache for WMS servers. It supports a large variety of " +
                        "protocols and formats, including WMS-C, WMTS, KML, Google Maps and Virtual Earth.")));
        assertThat(doc, hasXPath("count(/global/serviceInformation/keywords/string)", equalTo("3")));
        assertThat(doc, hasXPath("/global/serviceInformation/serviceProvider/providerName", equalTo("Jane Doe inc.")));
        assertThat(doc, hasXPath("/global/serviceInformation/serviceProvider/serviceContact/individualName", equalTo("Jane Doe")));
        assertThat(doc, hasXPath("/global/runtimeStatsEnabled", equalTo("false")));
        assertThat(doc, hasXPath("/global/wmtsCiteCompliant", equalTo("false")));
        assertThat(doc, hasXPath("/global/backendTimeout", equalTo("120")));
    }

    @Test
    public void testPutGlobalRoundTrip() throws Exception {
        testGetGlobal();

        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/global.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        final String globalUpdate = getResponseEntity(response);

        response = handlePut(URI.create("/geowebcache/rest/global"), admin.getClient(), globalUpdate);
        assertEquals(200, response.getStatusLine().getStatusCode());
        response.close();

        //Round-tripping the XML should not cause changes
        testGetGlobal();

    }

    @Test
    public void testPutGlobalPartial() throws Exception {

        //Only PUT a partial object
        final String globalUpdate = "<global><backendTimeout>150</backendTimeout></global>";

        testGetGlobal();

        CloseableHttpResponse  response = handlePut(URI.create("/geowebcache/rest/global"), admin.getClient(), globalUpdate);
        assertEquals(200, response.getStatusLine().getStatusCode());
        response.close();

        response = handleGet(URI.create("/geowebcache/rest/global.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        //Verify only the PUT value has changed
        assertThat(doc, hasXPath("/global/serviceInformation/title", equalTo("GeoWebCache")));
        assertThat(doc, hasXPath("normalize-space(/global/serviceInformation/description)", equalTo(
                "GeoWebCache is an advanced tile cache for WMS servers. It supports a large variety of " +
                        "protocols and formats, including WMS-C, WMTS, KML, Google Maps and Virtual Earth.")));
        assertThat(doc, hasXPath("count(/global/serviceInformation/keywords/string)", equalTo("4")));
        assertThat(doc, hasXPath("/global/serviceInformation/serviceProvider/providerName", equalTo("John Smith inc.")));
        assertThat(doc, hasXPath("/global/serviceInformation/serviceProvider/serviceContact/individualName", equalTo("John Smith")));
        assertThat(doc, hasXPath("/global/runtimeStatsEnabled", equalTo("true")));
        assertThat(doc, hasXPath("/global/wmtsCiteCompliant", equalTo("false")));
        assertThat(doc, hasXPath("/global/backendTimeout", equalTo("150")));
    }

    @Test
    public void testPutGlobalLock() throws Exception {

        final String globalUpdate = "<global><lockProvider>nioLock</lockProvider></global>";

        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/global.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        //Lock provider is null by default. If this is the case, it won't show up in the configuration
        assertThat(doc, hasXPath("count(/global/lockProvider)", equalTo("0")));

        response = handlePut(URI.create("/geowebcache/rest/global"), admin.getClient(), globalUpdate);
        assertEquals(200, response.getStatusLine().getStatusCode());
        response.close();

        response = handleGet(URI.create("/geowebcache/rest/global.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("/global/lockProvider", equalTo("nioLock")));
    }

    @Test
    public void testPutGlobalReadOnly() throws Exception {
        //PUT a value that is read-only
        final String globalUpdate = "<global><location>foobar</location></global>";

        CloseableHttpResponse  response = handlePut(URI.create("/geowebcache/rest/global"), admin.getClient(), globalUpdate);
        assertEquals(400, response.getStatusLine().getStatusCode());
        response.close();
    }

    /* TileLayerController Integration Tests *****************************************************/

    @Test
    public void testGetLayers() throws Exception {
        doGetXML(
            "rest/layers.xml",
            admin.getClient(),
            equalTo(200),
            doc->{
        
                assertThat(doc, 
                    hasXPath("/layers/layer[name/text()='img states']/atom:link/@href", 
                    equalTo(jetty.getUri().resolve("/geowebcache/rest/layers/img%20states.xml").toString())));
                assertThat(doc, 
                    hasXPath("/layers/layer[name/text()='topp:states']/atom:link/@href", 
                    equalTo(jetty.getUri().resolve("/geowebcache/rest/layers/topp:states.xml").toString())));
                assertThat(doc, 
                    hasXPath("/layers/layer[name/text()='raster test layer']/atom:link/@href", 
                    equalTo(jetty.getUri().resolve("/geowebcache/rest/layers/raster%20test%20layer.xml").toString())));
                
                assertThat(doc, 
                    hasXPath("count(/layers/layer)", 
                    equalTo("3")));
            });
        
    }
    
    @Test
    public void testCreateUpdateDelete() throws Exception {
        final String layerName = "testLayer";
        final String url1 = "http://example.com/wms1?";
        final String url2 = "http://example.com/wms2?";
        final String layers = "remoteLayer";
        
        // Create
        {
            final HttpPut request = new HttpPut(jetty.getUri().resolve("rest/layers/").resolve(layerName+".xml"));
            request.setEntity(new StringEntity("<wmsLayer><name>"+layerName+"</name><wmsUrl><string>"+url1+"</string></wmsUrl><wmsLayers>"+layers+"</wmsLayers></wmsLayer>", ContentType.APPLICATION_XML));
            try( CloseableHttpResponse response = admin.getClient().execute(request)) {
                assertThat(response.getStatusLine(), hasProperty("statusCode", equalTo(200)));
            }
            
            doGetXML(
                    "rest/layers.xml",
                    admin.getClient(),
                    equalTo(200),
                    doc->{
                        assertThat(doc, 
                            hasXPath("/layers/layer[name/text()='"+layerName+"']/atom:link/@href",
                            equalTo(jetty.getUri().resolve("/geowebcache/rest/layers/"+layerName+".xml").toString())));
                    });
            doGetXML(
                    "rest/layers/"+layerName+".xml",
                    admin.getClient(),
                    equalTo(200),
                    doc->{
                        assertThat(doc, 
                            hasXPath("/wmsLayer/name", equalTo(layerName)));
                        assertThat(doc, 
                                hasXPath("/wmsLayer/wmsUrl/string", equalTo(url1)));
                        assertThat(doc, 
                                hasXPath("/wmsLayer/wmsLayers", equalTo(layers)));
                    });
        }
        // Update
        {
            final HttpPost request = new HttpPost(jetty.getUri().resolve("rest/layers/").resolve(layerName+".xml"));
            request.setEntity(new StringEntity("<wmsLayer><name>"+layerName+"</name><wmsUrl><string>"+url2+"</string></wmsUrl><wmsLayers>"+layers+"</wmsLayers></wmsLayer>", ContentType.APPLICATION_XML));
            try( CloseableHttpResponse response = admin.getClient().execute(request)) {
                assertThat(response.getStatusLine(), hasProperty("statusCode", equalTo(200)));
            }
            doGetXML(
                    "rest/layers/"+layerName+".xml",
                    admin.getClient(),
                    equalTo(200),
                    doc->{
                        assertThat(doc, 
                            hasXPath("/wmsLayer/name", equalTo(layerName)));
                        assertThat(doc, 
                            hasXPath("/wmsLayer/wmsUrl/string", equalTo(url2)));
                        assertThat(doc, 
                            hasXPath("/wmsLayer/wmsLayers", equalTo(layers)));
                    });
        }
        // GetCap
        {
            doGetXML("service/wmts?REQUEST=getcapabilities",
                    anonymous.getClient(),
                    equalTo(200),
                    doc->{
                        assertThat(doc, 
                            hasXPath("/wmts:Capabilities/wmts:Contents/wmts:Layer/ows:Title[text()='"+layerName+"']"));
                    });
        }
        // Delete
        {
            final HttpDelete request = new HttpDelete(jetty.getUri().resolve("rest/layers/").resolve(layerName+".xml"));
            try( CloseableHttpResponse response = admin.getClient().execute(request)) {
                assertThat(response.getStatusLine(), hasProperty("statusCode", equalTo(200)));
            }
            
            doGetXML(
                    "rest/layers.xml",
                    admin.getClient(),
                    equalTo(200),
                    doc->{
                        assertThat(doc, 
                            not(hasXPath("/layers/layer[name/text()='"+layerName+"']")));
                    });
            
            final HttpGet request2 = new HttpGet(jetty.getUri().resolve("rest/layers/").resolve(layerName+".xml"));
            try( CloseableHttpResponse response = admin.getClient().execute(request2)) {
                assertThat(response.getStatusLine(), hasProperty("statusCode", equalTo(404)));
            }
        }
        // GetCap
        {
            doGetXML("service/wmts?REQUEST=getcapabilities",
                    anonymous.getClient(),
                    equalTo(200),
                    doc->{
                        assertThat(doc, 
                            not(hasXPath("/wmts:Capabilities/wmts:Contents/wmts:Layer/ows:Title[text()='"+layerName+"']")));
                    });
        }
       
    }
    
    @Test
    public void testInvalidMethods() throws Exception {
        // Check that all permutations of method and user produce the expected status code. 
        for(HttpUriRequest request: Arrays.asList(
                new HttpDelete(jetty.getUri().resolve("rest/layers.xml")),
                new HttpPost(jetty.getUri().resolve("rest/layers.xml")),
                new HttpPut(jetty.getUri().resolve("rest/layers.xml")),

                new HttpPut(jetty.getUri().resolve("rest/seed/img%20states.xml")),
                new HttpDelete(jetty.getUri().resolve("rest/seed/img%20states.xml")),
                new HttpDelete(jetty.getUri().resolve("rest/seed/ui_form")),
                new HttpPut(jetty.getUri().resolve("rest/seed/ui_form")),

                new HttpDelete(jetty.getUri().resolve("rest/diskquota.xml")),
                new HttpDelete(jetty.getUri().resolve("rest/diskquota.json")),
                new HttpDelete(jetty.getUri().resolve("rest/diskquota")),

                new HttpPut(jetty.getUri().resolve("rest/masstruncate")),
                new HttpDelete(jetty.getUri().resolve("rest/masstruncate")),

                new HttpPut(jetty.getUri().resolve("rest/statistics")),
                new HttpPost(jetty.getUri().resolve("rest/statistics")),
                new HttpDelete(jetty.getUri().resolve("rest/statistics")),
                
                new HttpPut(jetty.getUri().resolve("rest/reload")),
                new HttpGet(jetty.getUri().resolve("rest/reload")),
                new HttpDelete(jetty.getUri().resolve("rest/reload"))
                )) {
            testSecured(request, equalTo(405));
        }
    }
    
    @Test
    public void testSecure() throws Exception {
        for(HttpUriRequest request: Arrays.asList(
                new HttpGet(jetty.getUri().resolve("rest/layers.xml")),
                new HttpGet(jetty.getUri().resolve("rest/seed/img%20states")),
                new HttpPost(jetty.getUri().resolve("rest/reload")),
                new HttpPost(jetty.getUri().resolve("rest/seed/img%20states.xml")),
                new HttpGet(jetty.getUri().resolve("rest/seed/img%20states.xml")),
                new HttpGet(jetty.getUri().resolve("rest/seed/ui_form")),
                new HttpPost(jetty.getUri().resolve("rest/seed/ui_form")),
                new HttpGet(jetty.getUri().resolve("rest/masstruncate")),
                new HttpPost(jetty.getUri().resolve("rest/masstruncate"))
                )) {
            testSecured(request, not(either(equalTo(401)).or(equalTo(405))));
        }
    }
    
    @Test
    public void testGetLayer() throws Exception {
        doGetXML(
            "rest/layers/img%20states.xml",
            admin.getClient(),
            equalTo(200),
            doc->{
                assertThat(doc, 
                    hasXPath("/wmsLayer/name", 
                    equalTo("img states")));
                assertThat(doc, 
                    hasXPath("/wmsLayer/wmsUrl/string", 
                    equalTo("http://demo.opengeo.org/geoserver/wms?")));
                assertThat(doc, 
                    hasXPath("/wmsLayer/wmsLayers", 
                    equalTo("nurc:Img_Sample,topp:states")));
            });
    }
    
    @Test
    public void testLayerNoAuth() throws Exception {
        for(CloseableHttpClient client: Arrays.asList(
                anonymous.getClient(),
                notAUser.getClient()
                )){
            doGetXML(
                "rest/layers/img%20states.xml",
                client,
                equalTo(401),
                doc->{
                    assertThat(doc, 
                        not(
                        hasXPath("//wmsUrl", 
                        containsString("demo.opengeo.org"))));
                    assertThat(doc, 
                        not(
                        hasXPath("//wmsLayer", 
                        containsString("nurc"))));
                    assertThat(doc, 
                        not(
                        hasXPath("//wmsLayer", 
                        containsString("Img_Sample"))));
                    assertThat(doc, 
                        not(
                        hasXPath("//wmsLayer", 
                        containsString("topp"))));
                    assertThat(doc, 
                        not(
                        hasXPath("//wmsLayer", 
                        containsString("states"))));
                });
        }
    }
    
    /**
     * Check that the given request gives a 401 Forbidden when not authenticated, and otherwise 
     * has a response matching the given matcher
     * @param request
     * @param authenticatedStatus
     * @throws Exception
     */
    protected void testSecured(HttpUriRequest request, Matcher<Integer> authenticatedStatus) throws Exception {
        {
            CloseableHttpClient client = admin.getClient();
            try( CloseableHttpResponse response = client.execute(request);
                InputStream in = response.getEntity().getContent()) {
                assertThat(response.getStatusLine(), hasProperty("statusCode", authenticatedStatus));
            }
        }
        for(CloseableHttpClient client: Arrays.asList(
                anonymous.getClient(),
                notAUser.getClient(),
                badPassword.getClient()
                )) {
            try( CloseableHttpResponse response = client.execute(request);
                InputStream in = response.getEntity().getContent()) {
                final int code = 401;
                assertThat(response.getStatusLine(),
                    describedAs("Request %0 with without authentication produces status code %1",
                        hasProperty("statusCode", equalTo(code)), 
                        request, code));
            }
        }
    }
    
    @Test
    public void testAddLayer() throws Exception {
        doGetXML(
            "rest/layers/img%20states.xml",
            notAUser.getClient(),
            equalTo(401),
            doc->{
                assertThat(doc, 
                    not(
                    hasXPath("//wmsUrl", 
                    containsString("demo.opengeo.org"))));
                assertThat(doc, 
                    not(
                    hasXPath("//wmsLayer", 
                    containsString("nurc"))));
                assertThat(doc, 
                    not(
                    hasXPath("//wmsLayer", 
                    containsString("Img_Sample"))));
                assertThat(doc, 
                    not(
                    hasXPath("//wmsLayer", 
                    containsString("topp"))));
                assertThat(doc, 
                    not(
                    hasXPath("//wmsLayer", 
                    containsString("states"))));
            });
    }

    /* BlobStoreController Integration Tests *****************************************************/

    @Test
    public void testGetBlobStoresXML() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/blobstores.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("count(/blobStores/blobStore)", equalTo("1")));
        assertThat(doc, hasXPath("/blobStores/blobStore[1]/name", equalTo("defaultCache")));
        assertThat(doc, hasXPath("/blobStores/blobStore[1]/atom:link/@href",
                equalTo("http://localhost:8080/geowebcache/rest/blobstores/defaultCache.xml")));
        assertThat(doc, hasXPath("/blobStores/blobStore[1]/atom:link/@type",
                equalTo(MediaType.TEXT_XML_VALUE)));
    }

    @Test
    public void testGetBlobStoresJSON() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/blobstores.json"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        JSONArray jsonArray = getResponseEntityAsJSONArray(response);
        assertEquals(1, jsonArray.length());
        assertEquals("defaultCache", jsonArray.get(0));
    }

    @Test
    public void testGetBlobStoreXML() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/blobstores/defaultCache.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("//id", equalTo("defaultCache")));
        assertThat(doc, hasXPath("//enabled", equalTo("false")));
        assertThat(doc, hasXPath("//baseDirectory", equalTo("/tmp/defaultCache")));
        assertThat(doc, hasXPath("//fileSystemBlockSize", equalTo("4096")));
    }

    @Test
    public void testGetBlobStoreJSON() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/blobstores/defaultCache.json"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        JSONObject jsonObject = getResponseEntityAsJSONObject(response);
        jsonObject = jsonObject.getJSONObject("FileBlobStore");
        assertEquals("defaultCache", jsonObject.get("id"));
        assertEquals(false, jsonObject.get("enabled"));
        assertEquals("/tmp/defaultCache", jsonObject.get("baseDirectory"));
        assertEquals(4096, jsonObject.get("fileSystemBlockSize"));
    }

    @Test
    public void testPutBlobStoreCreateModifyDelete() throws Exception {
        String blobStore =
                "<FileBlobStore>\n" +
                "    <id>newCache</id>\n" +
                "    <enabled>false</enabled>\n" +
                "    <baseDirectory>/tmp/newCache</baseDirectory>\n" +
                "    <fileSystemBlockSize>4096</fileSystemBlockSize>\n" +
                "</FileBlobStore>";

        //Make it sure doesn't exist
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/blobstores/newCache.xml"), admin.getClient());
        assertEquals(404, response.getStatusLine().getStatusCode());
        response.close();

        response = handlePut(URI.create("/geowebcache/rest/blobstores/newCache"), admin.getClient(), blobStore);
        assertEquals(201, response.getStatusLine().getStatusCode());
        response.close();

        response = handleGet(URI.create("/geowebcache/rest/blobstores/newCache.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("//id", equalTo("newCache")));
        assertThat(doc, hasXPath("//enabled", equalTo("false")));
        assertThat(doc, hasXPath("//baseDirectory", equalTo("/tmp/newCache")));
        assertThat(doc, hasXPath("//fileSystemBlockSize", equalTo("4096")));

        String blobStoreUpdate =
                "<FileBlobStore>\n" +
                "    <id>newCache</id>\n" +
                "    <enabled>false</enabled>\n" +
                "    <baseDirectory>/tmp/newCache</baseDirectory>\n" +
                "    <fileSystemBlockSize>2048</fileSystemBlockSize>\n" +
                "</FileBlobStore>";

        response = handlePut(URI.create("/geowebcache/rest/blobstores/newCache"), admin.getClient(), blobStoreUpdate);
        assertEquals(200, response.getStatusLine().getStatusCode());
        response.close();

        response = handleGet(URI.create("/geowebcache/rest/blobstores/newCache.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("//id", equalTo("newCache")));
        assertThat(doc, hasXPath("//enabled", equalTo("false")));
        assertThat(doc, hasXPath("//baseDirectory", equalTo("/tmp/newCache")));
        assertThat(doc, hasXPath("//fileSystemBlockSize", equalTo("2048")));

        response = handleDelete(URI.create("/geowebcache/rest/blobstores/newCache.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
        response.close();

        response = handleGet(URI.create("/geowebcache/rest/blobstores/newCache.xml"), admin.getClient());
        assertEquals(404, response.getStatusLine().getStatusCode());
        response.close();
    }

    /* GridSetController Integration Tests *****************************************************/

    @Test
    public void testGetGridSetsXML() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/gridsets.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("count(/gridSets/gridSet)", equalTo("6")));
        assertThat(doc, hasXPath("/gridSets/gridSet[1]/name", equalTo("EPSG:2163")));
        assertThat(doc, hasXPath("/gridSets/gridSet[1]/atom:link/@href",
                equalTo("http://localhost:8080/geowebcache/rest/gridsets/EPSG:2163.xml")));
        assertThat(doc, hasXPath("/gridSets/gridSet[1]/atom:link/@type",
                equalTo(MediaType.TEXT_XML_VALUE)));
    }

    @Test
    public void testGetGridSetsJSON() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/gridsets.json"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        JSONArray jsonArray = getResponseEntityAsJSONArray(response);
        assertEquals(6, jsonArray.length());
        assertEquals("EPSG:2163", jsonArray.get(0));
    }

    @Test
    public void testGetGridSetXML() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/gridsets/EPSG:2163.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("//name", equalTo("EPSG:2163")));
        assertThat(doc, hasXPath("//srs/number", equalTo("2163")));
        assertThat(doc, hasXPath("//originalExtent/coords/double[1]", equalTo("-2495667.977678598")));
        assertThat(doc, hasXPath("//originalExtent/coords/double[2]", equalTo("-2223677.196231552")));
        assertThat(doc, hasXPath("//originalExtent/coords/double[3]", equalTo("3291070.6104286816")));
        assertThat(doc, hasXPath("//originalExtent/coords/double[4]", equalTo("959189.3312465074")));
        assertThat(doc, hasXPath("//gridLevels/org.geowebcache.grid.Grid[1]/numTilesWide", equalTo("5")));
        assertThat(doc, hasXPath("//gridLevels/org.geowebcache.grid.Grid[1]/numTilesHigh", equalTo("3")));
        assertThat(doc, hasXPath("//gridLevels/org.geowebcache.grid.Grid[1]/resolution", equalTo("6999.999999999999")));
        assertThat(doc, hasXPath("//gridLevels/org.geowebcache.grid.Grid[1]/scaleDenom", equalTo("2.5E7")));
        assertThat(doc, hasXPath("//yBaseToggle", equalTo("false")));
        assertThat(doc, hasXPath("//yCoordinateFirst", equalTo("false")));
        assertThat(doc, hasXPath("//resolutionsPreserved", equalTo("false")));

    }

    @Test
    public void testGetGridSetJSON() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/gridsets/EPSG:2163.json"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        JSONObject jsonObject = getResponseEntityAsJSONObject(response);
        jsonObject = jsonObject.getJSONObject("org.geowebcache.grid.GridSet");
        assertEquals("EPSG:2163", jsonObject.get("name"));
        assertEquals("2163", jsonObject.getJSONObject("srs").getString("number"));
        assertEquals("[-2495667.977678598,-2223677.196231552,3291070.6104286816,959189.3312465074]", jsonObject.getJSONObject("originalExtent").get("coords").toString());
        assertEquals("false", jsonObject.get("yBaseToggle").toString());
    }

    @Test
    public void testPutGridSetCreateModifyDelete() throws Exception {
        String gridSet =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
                "<org.geowebcache.grid.GridSet>\n"+
                "  <name>testGridset</name>\n"+
                "  <srs>\n"+
                "    <number>4326</number>\n"+
                "  </srs>\n"+
                "  <tileWidth>211</tileWidth>\n"+
                "  <tileHeight>211</tileHeight>\n"+
                "  <yBaseToggle>false</yBaseToggle>\n"+
                "  <yCoordinateFirst>false</yCoordinateFirst>\n"+
                "  <scaleWarning>false</scaleWarning>\n"+
                "  <metersPerUnit>1.0</metersPerUnit>\n"+
                "  <pixelSize>2.8E-4</pixelSize>\n"+
                "  <originalExtent>\n"+
                "    <coords>\n"+
                "      <double>-99999.99</double>/n"+
                "      <double>-99999.99</double>/n"+
                "      <double>99999.99</double>/n"+
                "      <double>99999.99</double>/n"+
                "    </coords>/n"+
                "  </originalExtent>/n"+
                " <gridLevels>/n"+
                "    <org.geowebcache.grid.Grid>/n"+
                "      <numTilesWide>1</numTilesWide>/n"+
                "      <numTilesHigh>1</numTilesHigh>/n"+
                "      <resolution>5000.0</resolution>/n"+
                "      <scaleDenom>2.5E7</scaleDenom>/n"+
                "      <name>EPSG:4326:0</name>/n"+
                "    </org.geowebcache.grid.Grid>/n"+
                "    <org.geowebcache.grid.Grid>/n"+
                "      <numTilesWide>100</numTilesWide>\n"+
                "      <numTilesHigh>100</numTilesHigh>\n"+
                "      <resolution>250.0</resolution>\n"+
                "      <scaleDenom>1000000.0</scaleDenom>\n"+
                "      <name>EPSG:4326:1</name>\n"+
                "    </org.geowebcache.grid.Grid>\n"+
                "    <org.geowebcache.grid.Grid>\n"+
                "      <numTilesWide>1000</numTilesWide>\n"+
                "      <numTilesHigh>1000</numTilesHigh>\n"+
                "      <resolution>25.0</resolution>\n"+
                "      <scaleDenom>100000.0</scaleDenom>\n"+
                "      <name>EPSG:4326:2</name>\n"+
                "    </org.geowebcache.grid.Grid>\n"+
                "    <org.geowebcache.grid.Grid>\n"+
                "      <numTilesWide>2000</numTilesWide>\n"+
                "      <numTilesHigh>2000</numTilesHigh>\n"+
                "      <resolution>5.0</resolution>\n"+
                "      <scaleDenom>25000.0</scaleDenom>\n"+
                "      <name>EPSG:4326:3</name>\n"+
                "    </org.geowebcache.grid.Grid>\n"+
                "  </gridLevels>\n"+
                "  <resolutionsPreserved>false</resolutionsPreserved>\n"+
                "</org.geowebcache.grid.GridSet>";

        //Make it sure doesn't exist
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/gridsets/testGridset.xml"), admin.getClient());
        assertEquals(404, response.getStatusLine().getStatusCode());
        response.close();

        response = handlePut(URI.create("/geowebcache/rest/gridsets/testGridset.xml"), admin.getClient(), gridSet);
        assertEquals(201, response.getStatusLine().getStatusCode());
        response.close();

        response = handleGet(URI.create("/geowebcache/rest/gridsets/testGridset.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        Document doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("//name", equalTo("testGridset")));
        assertThat(doc, hasXPath("//srs/number", equalTo("4326")));
        assertThat(doc, hasXPath("//originalExtent/coords/double[1]", equalTo("-99999.99")));
        assertThat(doc, hasXPath("//resolutionsPreserved", equalTo("false")));

        String gridSetUpdate =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
                "<org.geowebcache.grid.GridSet>\n"+
                "  <name>testGridset</name>\n"+
                "  <srs>\n"+
                "    <number>2163</number>\n"+
                "  </srs>\n"+
                "  <tileWidth>200</tileWidth>\n"+
                "  <tileHeight>200</tileHeight>\n"+
                "  <yBaseToggle>false</yBaseToggle>\n"+
                "  <yCoordinateFirst>false</yCoordinateFirst>\n"+
                "  <scaleWarning>false</scaleWarning>\n"+
                "  <metersPerUnit>1.0</metersPerUnit>\n"+
                "  <pixelSize>2.8E-4</pixelSize>\n"+
                "  <originalExtent>\n"+
                "    <coords>\n"+
                "      <double>-2495667.977678598</double>/n"+
                "      <double>-2223677.196231552</double>/n"+
                "      <double>3291070.6104286816</double>/n"+
                "      <double>959189.3312465074</double>/n"+
                "    </coords>/n"+
                "  </originalExtent>/n"+
                " <gridLevels>/n"+
                "    <org.geowebcache.grid.Grid>/n"+
                "      <numTilesWide>5</numTilesWide>/n"+
                "      <numTilesHigh>3</numTilesHigh>/n"+
                "      <resolution>6999.999999999999</resolution>/n"+
                "      <scaleDenom>2.5E7</scaleDenom>/n"+
                "      <name>EPSG:2163:0</name>/n"+
                "    </org.geowebcache.grid.Grid>/n"+
                "    <org.geowebcache.grid.Grid>/n"+
                "      <numTilesWide>104</numTilesWide>\n"+
                "      <numTilesHigh>57</numTilesHigh>\n"+
                "      <resolution>280.0</resolution>\n"+
                "      <scaleDenom>1000000.0</scaleDenom>\n"+
                "      <name>EPSG:2163:1</name>\n"+
                "    </org.geowebcache.grid.Grid>\n"+
                "    <org.geowebcache.grid.Grid>\n"+
                "      <numTilesWide>1034</numTilesWide>\n"+
                "      <numTilesHigh>569</numTilesHigh>\n"+
                "      <resolution>27.999999999999996</resolution>\n"+
                "      <scaleDenom>100000.0</scaleDenom>\n"+
                "      <name>EPSG:2163:2</name>\n"+
                "    </org.geowebcache.grid.Grid>\n"+
                "    <org.geowebcache.grid.Grid>\n"+
                "      <numTilesWide>4134</numTilesWide>\n"+
                "      <numTilesHigh>2274</numTilesHigh>\n"+
                "      <resolution>6.999999999999999</resolution>\n"+
                "      <scaleDenom>25000.0</scaleDenom>\n"+
                "      <name>EPSG:2163:3</name>\n"+
                "    </org.geowebcache.grid.Grid>\n"+
                "  </gridLevels>\n"+
                "  <resolutionsPreserved>false</resolutionsPreserved>\n"+
                "</org.geowebcache.grid.GridSet>\n";

        response = handlePut(URI.create("/geowebcache/rest/gridsets/testGridset"), admin.getClient(), gridSetUpdate);
        assertEquals(200, response.getStatusLine().getStatusCode());
        response.close();

        response = handleGet(URI.create("/geowebcache/rest/gridsets/testGridset.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());

        doc = getResponseEntityAsXML(response);

        assertThat(doc, hasXPath("//name", equalTo("testGridset")));
        assertThat(doc, hasXPath("//srs/number", equalTo("2163")));
        assertThat(doc, hasXPath("//originalExtent/coords/double[1]", equalTo("-2495667.977678598")));
        assertThat(doc, hasXPath("//originalExtent/coords/double[2]", equalTo("-2223677.196231552")));
        assertThat(doc, hasXPath("//originalExtent/coords/double[3]", equalTo("3291070.6104286816")));
        assertThat(doc, hasXPath("//originalExtent/coords/double[4]", equalTo("959189.3312465074")));
        assertThat(doc, hasXPath("//gridLevels/org.geowebcache.grid.Grid[1]/numTilesWide", equalTo("5")));
        assertThat(doc, hasXPath("//gridLevels/org.geowebcache.grid.Grid[1]/numTilesHigh", equalTo("3")));
        assertThat(doc, hasXPath("//gridLevels/org.geowebcache.grid.Grid[1]/resolution", equalTo("6999.999999999999")));
        assertThat(doc, hasXPath("//gridLevels/org.geowebcache.grid.Grid[1]/scaleDenom", equalTo("2.5E7")));
        assertThat(doc, hasXPath("//yBaseToggle", equalTo("false")));
        assertThat(doc, hasXPath("//yCoordinateFirst", equalTo("false")));
        assertThat(doc, hasXPath("//resolutionsPreserved", equalTo("false")));

        response = handleDelete(URI.create("/geowebcache/rest/gridsets/testGridset.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
        response.close();

        response = handleGet(URI.create("/geowebcache/rest/gridsets/testGridset.xml"), admin.getClient());
        assertEquals(404, response.getStatusLine().getStatusCode());
        response.close();
    }


    /* DiskQuotaController Integration Tests *****************************************************/

    @Test
    public void testDiskQuotaXML() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/diskquota.xml"), admin.getClient());
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
        if (response.getStatusLine().getStatusCode() == 200) {
            Document doc = getResponseEntityAsXML(response);
            assertThat(doc,
                    hasXPath("//enabled",
                            equalTo("false")));
            assertThat(doc,
                    hasXPath("//cacheCleanUpFrequency",
                            equalTo("10")));
            assertThat(doc,
                    hasXPath("//cacheCleanUpUnits",
                            equalTo("SECONDS")));
            assertThat(doc,
                    hasXPath("//maxConcurrentCleanUps",
                            equalTo("2")));
            assertThat(doc,
                    hasXPath("//globalExpirationPolicyName",
                            equalTo("LFU")));
            assertThat(doc,
                    hasXPath("//globalQuota/id",
                            equalTo("0")));
            assertThat(doc,
                    hasXPath("//globalQuota/bytes",
                            equalTo("524288000")));
        }
    }

    @Test
    public void testDiskQuotaJson() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/diskquota.json"), admin.getClient());
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
        if (response.getStatusLine().getStatusCode() == 200) {
            JSONObject jsonObject = getResponseEntityAsJSONObject(response);
            Object obj = jsonObject.get("org.geowebcache.diskquota.DiskQuotaConfig");
            if (obj instanceof JSONObject) {
                TestCase.assertEquals(false, ((JSONObject) obj).get("enabled"));
                TestCase.assertEquals(10, ((JSONObject) obj).get("cacheCleanUpFrequency"));
                TestCase.assertEquals("SECONDS", ((JSONObject) obj).get("cacheCleanUpUnits"));
                TestCase.assertEquals(2, ((JSONObject) obj).get("maxConcurrentCleanUps"));
                TestCase.assertEquals("LFU", ((JSONObject) obj).get("globalExpirationPolicyName"));
                Object globalQuota = ((JSONObject) obj).get("globalQuota");
                if (globalQuota instanceof JSONObject) {
                    TestCase.assertEquals(0, ((JSONObject) globalQuota).get("id"));
                    TestCase.assertEquals(524288000, ((JSONObject) globalQuota).get("bytes"));
                }
            }

        }
    }

    /* SeedController Integration Tests ***********************************************************/

    @Test
    public void testSeedPost() throws Exception{
        String seedLayer = "<seedRequest>" + //
                "  <name>topp:states</name>" + //
                "  <srs>" + //
                "    <number>4326</number>" + //
                "  </srs>" + //
                "  <zoomStart>1</zoomStart>" + //
                "  <zoomStop>12</zoomStop>" + //
                "  <format>image/png</format>" + //
                "  <type>truncate</type>" + //
                "  <threadCount>4</threadCount>" + //
                "</seedRequest>";

        CloseableHttpResponse response = handlePost(URI.create("/geowebcache/rest/seed/topp:states.xml"),
                admin.getClient(), seedLayer);
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGet() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed/topp:states"),
                admin.getClient());
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGetNoLayer() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed"),
                admin.getClient());
        TestCase.assertEquals(405, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGetSeedForm() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed/topp:states"),
                admin.getClient());
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGetJson() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed.json"),
                admin.getClient());
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGetLayerJson() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed/topp:states.json"),
                admin.getClient());
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGetLayerXml() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed/topp:states.xml"),
                admin.getClient());
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testKillAll() throws Exception {
        String killCommand = "kill_all=all";
        CloseableHttpResponse response = handlePost(URI.create("/geowebcache/rest/seed"),
                admin.getClient(), killCommand);
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testLayerKillAll() throws Exception {
        String killCommand = "kill_all=all";
        CloseableHttpResponse response = handlePost(URI.create("/geowebcache/rest/seed/topp:states"),
                admin.getClient(), killCommand);
        TestCase.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    /* Utility methods ***************************************************************************/

    private Document getResponseEntityAsXML(CloseableHttpResponse response) throws Exception {
        Document doc;

        doc = XMLUnit.buildTestDocument(new InputSource(response.getEntity().getContent()));
        doc.normalizeDocument();

        return doc;
    }

    private JSONObject getResponseEntityAsJSONObject(CloseableHttpResponse response) throws Exception {
        JSONObject jsonObject = new JSONObject(getResponseEntity(response));
        return jsonObject;
    }

    private JSONArray getResponseEntityAsJSONArray(CloseableHttpResponse response) throws Exception {
        JSONArray jsonObject = new JSONArray(getResponseEntity(response));
        return jsonObject;
    }

    private String getResponseEntity(CloseableHttpResponse response) {
        String doc;
        try {
            StringWriter writer = new StringWriter();
            IOUtils.copy(response.getEntity().getContent(), writer, null);
            doc = writer.toString();
        } catch (IOException e) {

            doc = e.getMessage().toString();
        }
        return doc;
    }
    private CloseableHttpResponse handleGet(URI uri, CloseableHttpClient client) throws Exception {
        HttpGet request = new HttpGet(jetty.getUri().resolve(uri));
        CloseableHttpResponse response = client.execute(request);
        return response;
    }

    interface Assertions<T> {
        void accept(T result) throws Exception;
    }

    void doGetXML(String uri, CloseableHttpClient client, Matcher<Integer> statusMatcher, Assertions<Document> body) throws Exception {
        doGetXML(URI.create(uri), client, statusMatcher, body);
    }

    void doGetXML(URI uri, CloseableHttpClient client, Matcher<Integer> statusMatcher, Assertions<Document> body) throws Exception {
        final HttpGet request = new HttpGet(jetty.getUri().resolve(uri));
        final Document doc;
        try( CloseableHttpResponse response = client.execute(request);
             InputStream in = response.getEntity().getContent()) {
            if (response.getStatusLine().getStatusCode() != 401) {
                doc = XMLUnit.buildTestDocument(new InputSource(in));
                body.accept(doc);
            }
            assertThat(response.getStatusLine(), hasProperty("statusCode",statusMatcher));
        }

    }

    private CloseableHttpResponse handleDelete(URI uri, CloseableHttpClient client) throws Exception {
        HttpDelete request = new HttpDelete(jetty.getUri().resolve(uri));
        CloseableHttpResponse response = client.execute(request);
        return response;
    }

    private CloseableHttpResponse handlePut(URI uri, CloseableHttpClient client, String data) throws Exception {
        HttpPut request = new HttpPut(jetty.getUri().resolve(uri));
        StringEntity entity = new StringEntity(data);
        entity.setContentType(new BasicHeader("Content-type", "text/xml"));
        request.setEntity(entity);
        CloseableHttpResponse response = client.execute(request);
        return response;
    }

    private CloseableHttpResponse handlePost(URI uri, CloseableHttpClient client, String data) throws Exception {
        HttpPost request = new HttpPost(jetty.getUri().resolve(uri));
        StringEntity entity = new StringEntity(data);
        entity.setContentType(new BasicHeader("Content-type", "text/xml"));
        request.setEntity(entity);
        CloseableHttpResponse response = client.execute(request);
        return response;
    }
}
