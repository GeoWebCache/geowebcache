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
package org.geowebcache.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.geotools.xml.XMLUtils;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class GeoWebCacheXMLUpdateTest {

    private GeoWebCacheXMLUpdate update;

    @Before
    public void setUp() throws Exception {
        update = new GeoWebCacheXMLUpdate();
    }

    @Test
    public void testTemplateAlreadySorted() throws Exception {
        try (InputStream is = XMLConfiguration.class.getResourceAsStream("/geowebcache.xml")) {
            assertFalse(update.update(parse(is)));
        }
    }

    @Test
    public void testLatestConfigurationAlreadySorted() throws Exception {
        try (InputStream is = XMLConfiguration.class.getResourceAsStream("geowebcache_200.xml")) {
            assertFalse(update.update(parse(is)));
        }
    }

    @Test
    public void testSortWithComments() throws Exception {
        String xml =
                """
                <gwcConfiguration xmlns="http://geowebcache.org/schema/2.0.0">
                  <version>2.0.0</version>
                  <layers>
                    <wmsLayer>
                      <name>states</name>
                      <!-- <blobStoreId>store</blobStoreId> -->
                      <wmsUrl><string>http://localhost/wms</string></wmsUrl>
                    </wmsLayer>
                  </layers>
                  <!-- seeder pool -->
                  <!-- <seederCorePoolSize>16</seederCorePoolSize> -->
                  <backendTimeout>120</backendTimeout> <!-- seconds -->
                  <gridSets/>
                  <!-- end -->
                </gwcConfiguration>
                """;
        Element root = parse(new ByteArrayInputStream(xml.getBytes(UTF_8)));

        assertTrue(update.update(root));
        assertEquals(
                List.of(
                        "version",
                        "backendTimeout",
                        "seconds",
                        "seeder pool",
                        "<seederCorePoolSize>16</seederCorePoolSize>",
                        "gridSets",
                        "layers",
                        "end"),
                outline(root));
        Element wmsLayer =
                (Element) root.getElementsByTagNameNS("*", "wmsLayer").item(0);
        assertEquals(List.of("<blobStoreId>store</blobStoreId>", "name", "wmsUrl"), outline(wmsLayer));
        XMLConfiguration.validate(root);
        assertFalse(update.update(root));
    }

    @Test
    public void testUnknownElementFollowsPredecessor() throws Exception {
        String xml =
                """
                <gwcConfiguration xmlns="http://geowebcache.org/schema/2.0.0">
                  <layers/>
                  <custom/>
                  <version>2.0.0</version>
                </gwcConfiguration>
                """;
        Element root = parse(new ByteArrayInputStream(xml.getBytes(UTF_8)));

        assertTrue(update.update(root));
        assertEquals(List.of("version", "layers", "custom"), outline(root));
    }

    private static List<String> outline(Element element) {
        List<String> outline = new ArrayList<>();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) {
                outline.add(node.getLocalName());
            } else if (node instanceof Comment comment) {
                outline.add(comment.getData().trim());
            }
        }
        return outline;
    }

    private static Element parse(InputStream is) throws Exception {
        DocumentBuilderFactory factory = XMLUtils.newDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        return XMLUtils.newDocumentBuilder(factory).parse(is).getDocumentElement();
    }
}
