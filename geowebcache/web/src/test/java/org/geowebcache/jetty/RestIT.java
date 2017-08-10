package org.geowebcache.jetty;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.describedAs;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.custommonkey.xmlunit.XMLUnit;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Integration test for the REST API in a full GWC instance
 * 
 * @author Kevin Smith, Boundless
 *
 */
public class RestIT {
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
                    equalTo(jetty.getUri().resolve("rest/layers/img+states.xml").toString())));
                assertThat(doc, 
                    hasXPath("/layers/layer[name/text()='topp:states']/atom:link/@href", 
                    equalTo(jetty.getUri().resolve("rest/layers/topp%3Astates.xml").toString())));
                assertThat(doc, 
                    hasXPath("/layers/layer[name/text()='raster test layer']/atom:link/@href", 
                    equalTo(jetty.getUri().resolve("rest/layers/raster+test+layer.xml").toString())));
                
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
                            equalTo(jetty.getUri().resolve("rest/layers/"+layerName+".xml").toString())));
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
                
                new HttpPut(jetty.getUri().resolve("rest/seed/img+states.xml")),
                new HttpDelete(jetty.getUri().resolve("rest/seed/img+states.xml")),
                new HttpDelete(jetty.getUri().resolve("rest/seed")),
                new HttpPut(jetty.getUri().resolve("rest/seed")),
                
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
                new HttpGet(jetty.getUri().resolve("rest/seed/img+states")),
                new HttpPost(jetty.getUri().resolve("rest/reload")),
                new HttpPost(jetty.getUri().resolve("rest/seed/img+states.xml")),
                new HttpGet(jetty.getUri().resolve("rest/seed/img+states.xml")),
                new HttpGet(jetty.getUri().resolve("rest/seed")),
                new HttpPost(jetty.getUri().resolve("rest/seed")),
                new HttpGet(jetty.getUri().resolve("rest/masstruncate")),
                new HttpPost(jetty.getUri().resolve("rest/masstruncate"))
                )) {
            testSecured(request, not(either(equalTo(401)).or(equalTo(405))));
        }
    }
    
    @Test
    public void testGetLayer() throws Exception {
        doGetXML(
            "rest/layers/img+states.xml",
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
                "rest/layers/img+states.xml",
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
            "rest/layers/img+states.xml",
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
