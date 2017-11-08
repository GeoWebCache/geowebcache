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

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.custommonkey.xmlunit.XMLUnit;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
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

/**
 * Integration test for the REST API in a full GWC instance *
 */
public class RestIT {
    private MockMvc mockMvc;

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
    public void setUp() {
        nsContext = new SimpleNamespaceContext();
        nsContext.bindNamespaceUri("atom", "http://www.w3.org/2005/Atom");
        nsContext.bindNamespaceUri("wmts", "http://www.opengis.net/wmts/1.0");
        nsContext.bindNamespaceUri("ows", "http://www.opengis.net/ows/1.1");
    }
    
    Matcher<Node> hasXPath(final String xpathExpr, final Matcher<String> matcher) {
        return Matchers.hasXPath(xpathExpr, nsContext, matcher);
        
    }
    Matcher<Node> hasXPath(final String xpathExpr) {
        return Matchers.hasXPath(xpathExpr, nsContext);
    }
    
    @Test
    public void testGetLayers() throws Exception {
        doGetXML(
            "rest/layers.xml",
            admin.getClient(),
            equalTo(200),
            doc->{
        
                assertThat(doc, 
                    hasXPath("/layers/layer[name/text()='img states']/atom:link/@href", 
                    equalTo(jetty.getUri().resolve("/geowebcache/layers/img+states.xml").toString())));
                assertThat(doc, 
                    hasXPath("/layers/layer[name/text()='topp:states']/atom:link/@href", 
                    equalTo(jetty.getUri().resolve("/geowebcache/layers/topp%3Astates.xml").toString())));
                assertThat(doc, 
                    hasXPath("/layers/layer[name/text()='raster test layer']/atom:link/@href", 
                    equalTo(jetty.getUri().resolve("/geowebcache/layers/raster+test+layer.xml").toString())));
                
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
                            equalTo(jetty.getUri().resolve("/geowebcache/layers/"+layerName+".xml").toString())));
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
    /*
    DISK QUOTA CONTROLLER TEST
     */
    @Test
    public void testDiskQuotaXML() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/diskquota.xml"), admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
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
        assertEquals(200, response.getStatusLine().getStatusCode());
        if (response.getStatusLine().getStatusCode() == 200) {
            JSONObject jsonObject = getResponseEntityAsJSON(response);
            Object obj = jsonObject.get("org.geowebcache.diskquota.DiskQuotaConfig");
            if (obj instanceof JSONObject) {
                assertEquals(false, ((JSONObject) obj).get("enabled"));
                assertEquals(10, ((JSONObject) obj).get("cacheCleanUpFrequency"));
                assertEquals("SECONDS", ((JSONObject) obj).get("cacheCleanUpUnits"));
                assertEquals(2, ((JSONObject) obj).get("maxConcurrentCleanUps"));
                assertEquals("LFU", ((JSONObject) obj).get("globalExpirationPolicyName"));
                Object globalQuota = ((JSONObject) obj).get("globalQuota");
                if (globalQuota instanceof JSONObject) {
                    assertEquals(0, ((JSONObject) globalQuota).get("id"));
                    assertEquals(524288000, ((JSONObject) globalQuota).get("bytes"));
                }
            }

        }
    }


    private Document getResponseEntityAsXML(CloseableHttpResponse response) throws Exception {
        Document doc;

        doc = XMLUnit.buildTestDocument(new InputSource(response.getEntity().getContent()));
        doc.normalizeDocument();

        return doc;
    }

    private JSONObject getResponseEntityAsJSON(CloseableHttpResponse response) throws Exception {
        JSONObject jsonObject = new JSONObject(getResponseEntity(response));
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
        public void accept(T result) throws Exception;
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
}
