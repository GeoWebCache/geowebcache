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
 * <p>Copyright 2026
 */
package org.geowebcache.georss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.geowebcache.util.URLs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GeoRSSReaderFactoryTest {

    private static final String ENTRY = "  <entry>\n"
            + "    <title>Mid Texas</title>\n"
            + "    <id>urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a</id>\n"
            + "    <updated>2005-08-17T07:02:33Z</updated>\n"
            + "    <georss:where>\n"
            + "      <gml:Point srsName=\"urn:x-ogc:def:crs:EPSG:6.11.2:4326\">\n"
            + "        <gml:pos>-98.67150 31.11915</gml:pos>\n"
            + "      </gml:Point>\n"
            + "    </georss:where>\n"
            + "  </entry>\n";

    private HttpServer server;

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/point_feed", exchange -> {
            try (InputStream in = getClass().getResourceAsStream("test-data/point_feed.xml")) {
                send(exchange, in.readAllBytes());
            }
        });
        server.createContext("/large_feed", exchange -> {
            StringBuilder feed = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:georss=\"http://www.georss.org/georss\""
                    + " xmlns:gml=\"http://www.opengis.net/gml\">\n"
                    + "  <title>Large feed</title>\n");
            for (int i = 0; i < 200; i++) {
                feed.append(ENTRY);
            }
            feed.append("</feed>\n");
            send(exchange, feed.toString().getBytes(StandardCharsets.UTF_8));
        });
        server.start();
    }

    private static void send(HttpExchange exchange, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/atom+xml; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @After
    public void stopServer() {
        server.stop(0);
    }

    private URL url(String path) throws IOException {
        return URLs.of("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static int countEntries(GeoRSSReader reader) throws IOException {
        int count = 0;
        Entry entry;
        while ((entry = reader.nextEntry()) != null) {
            assertNotNull(entry.getWhere());
            count++;
        }
        return count;
    }

    @Test
    public void testCreateReaderFromUrl() throws Exception {
        GeoRSSReader reader = new GeoRSSReaderFactory().createReader(url("/point_feed"), null, null);
        assertEquals(3, countEntries(reader));
    }

    @Test
    public void testCreateReaderFromUrlWithCredentials() throws Exception {
        GeoRSSReader reader = new GeoRSSReaderFactory().createReader(url("/point_feed"), "user", "secret");
        assertEquals(3, countEntries(reader));
    }

    @Test
    public void testCreateReaderFromUrlReadsWholeFeed() throws Exception {
        GeoRSSReader reader = new GeoRSSReaderFactory().createReader(url("/large_feed"), null, null);
        assertEquals(200, countEntries(reader));
    }
}
