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
 * @author David Vick, Boundless, Copyright 2017
 */

package org.geowebcache.jetty;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
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
import java.io.StringWriter;
import java.net.URI;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Integration test for the REST API in a full GWC instance
 * 
 * @author David Vick, Boundless
 *
 */
public class SpringRestIT {
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
    private MockMvc mockMvc;
    
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
    
    /*
    SEED CONTROLLER TESTS
     */
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
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGet() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed/topp:states"),
                admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGetNoLayer() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed"),
                admin.getClient());
        assertEquals(405, response.getStatusLine().getStatusCode());
    }
    
    @Test
    public void testSeedGetSeedForm() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed/topp:states"),
                admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
    }
    
    @Test
    public void testGetLogo() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/web/geowebcache_logo.png"),
                anonymous.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
    }
    
    @Test
    public void testGetCss() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/web/gwc.css"),
                anonymous.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
    }
    
    @Test
    public void testGetBadWebResource() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/web/thisDoesNotExist"),
                anonymous.getClient());
        assertEquals(404, response.getStatusLine().getStatusCode());
    }
    
    @Test
    public void testSeedGetJson() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed.json"),
                admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGetLayerJson() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed/topp:states.json"),
                admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testSeedGetLayerXml() throws Exception {
        CloseableHttpResponse response = handleGet(URI.create("/geowebcache/rest/seed/topp:states.xml"),
                admin.getClient());
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testKillAll() throws Exception {
        String killCommand = "kill_all=all";
        CloseableHttpResponse response = handlePost(URI.create("/geowebcache/rest/seed"),
                admin.getClient(), killCommand);
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testLayerKillAll() throws Exception {
        String killCommand = "kill_all=all";
        CloseableHttpResponse response = handlePost(URI.create("/geowebcache/rest/seed/topp:states"),
                admin.getClient(), killCommand);
        assertEquals(200, response.getStatusLine().getStatusCode());
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
            e.printStackTrace();
            doc = e.getMessage().toString();
        }
        return doc;
    }

    private CloseableHttpResponse handleGet(URI uri, CloseableHttpClient client) throws Exception {
        HttpGet request = new HttpGet(jetty.getUri().resolve(uri));
        CloseableHttpResponse response = client.execute(request);
        return response;
    }

    private CloseableHttpResponse handleDelete(URI uri, CloseableHttpClient client) throws Exception {
        HttpDelete request = new HttpDelete(jetty.getUri().resolve(uri));
        CloseableHttpResponse response = client.execute(request);
        return response;
    }

    private CloseableHttpResponse handlePut(URI uri, CloseableHttpClient client, String data) throws Exception {
        HttpPut request = new HttpPut(jetty.getUri().resolve(uri));
        StringEntity entity = new StringEntity(data);
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
