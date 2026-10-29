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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.geotools.xml.XMLUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.ImageMime;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class XMLConfigurationBackwardsCompatibilityTest {

    public static final String GWC_125_CONFIG_FILE = "geowebcache_125.xml";

    public static final String GWC_130_CONFIG_FILE = "geowebcache_130.xml";

    public static final String LATEST_FILENAME = "geowebcache_200.xml";

    private static final List<String> UPGRADE_FILES = List.of(
            "geowebcache_126.xml",
            "geowebcache_130.xml",
            "geowebcache_140.xml",
            "geowebcache_150.xml",
            "geowebcache_151.xml",
            "geowebcache_153.xml",
            "geowebcache_160.xml",
            "geowebcache_161.xml",
            "geowebcache_170.xml",
            "geowebcache_180.xml",
            "geowebcache_190.xml",
            "geowebcache_1100.xml",
            "geowebcache_1110.xml",
            "geowebcache_1120.xml",
            "geowebcache_1130.xml",
            "geowebcache_1140.xml",
            "geowebcache_1150.xml",
            "geowebcache_1170.xml",
            "geowebcache_1180.xml",
            "geowebcache_1190.xml",
            "geowebcache_1200.xml",
            "geowebcache_1210.xml",
            "geowebcache_1220.xml",
            "geowebcache_1230.xml",
            "geowebcache_1240.xml",
            "geowebcache_1250.xml",
            "geowebcache_1260.xml",
            "geowebcache_1270.xml",
            "geowebcache_1280.xml",
            "geowebcache_200.xml");

    @Test
    public void testLoadPre10() throws Exception {
        Iterable<TileLayer> layers = loadResource("geowebcache_pre10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertNotNull(layer);
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertNotNull(layer2);
        assertNotNull(grid);
    }

    @Test
    public void testLoad10() throws Exception {
        Iterable<TileLayer> layers = loadResource("geowebcache_10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertNotNull(layer);
        // assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertNotNull(layer2);
        assertNotNull(grid);
    }

    @Test
    public void testLoad101() throws Exception {
        Iterable<TileLayer> layers = loadResource("geowebcache_101.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertNotNull(layer);
        // assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertNotNull(layer2);
        assertNotNull(grid);

        // The additions in 1.0.1 are allowCacheBypass and backendTimeout
        assertEquals(60, layer.getBackendTimeout().intValue());
        assertEquals(235, layer2.getBackendTimeout().intValue());
        assertTrue(layer.isCacheBypassAllowed().booleanValue());
        assertFalse(layer2.isCacheBypassAllowed().booleanValue());
    }

    @Test
    public void testLoad114() throws Exception {
        Iterable<TileLayer> layers = loadResource("geowebcache_114.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertNotNull(layer);
        // assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertNotNull(layer2);
        assertNotNull(grid);

        // The additions in 1.0.1 are allowCacheBypass and backendTimeout
        assertEquals(120, layer.getBackendTimeout().intValue());
        assertEquals(120, layer2.getBackendTimeout().intValue());
        assertTrue(layer.isCacheBypassAllowed().booleanValue());
        assertTrue(layer2.isCacheBypassAllowed().booleanValue());

        FormatModifier fm = layer.getFormatModifier(ImageMime.jpeg);
        assertEquals("0xDDDDDD", fm.getBgColor());
        assertEquals(ImageMime.png, fm.getRequestFormat());

        List<RequestFilter> filters = layer.getRequestFilters();
        assertEquals("testWMSRasterFilter", filters.get(0).getName());
        assertEquals("testFileRasterFilter", filters.get(1).getName());
    }

    @Test
    public void testLoad115() throws Exception {
        Iterable<TileLayer> layers = loadResource("geowebcache_115.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertNotNull(layer);
        // assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertNotNull(layer2);
        assertNotNull(grid);

        // The additions in 1.0.1 are allowCacheBypass and backendTimeout
        assertEquals(120, layer.getBackendTimeout().intValue());
        assertEquals(120, layer2.getBackendTimeout().intValue());
        assertTrue(layer.isCacheBypassAllowed().booleanValue());
        assertTrue(layer2.isCacheBypassAllowed().booleanValue());

        FormatModifier fm = layer.getFormatModifier(ImageMime.jpeg);
        assertEquals("0xDDDDDD", fm.getBgColor());
        assertEquals(ImageMime.png, fm.getRequestFormat());

        List<RequestFilter> filters = layer.getRequestFilters();
        RequestFilter filter0 = filters.get(0);
        assertEquals("testWMSRasterFilter", filter0.getName());
        RequestFilter filter1 = filters.get(1);
        assertEquals("testFileRasterFilter", filter1.getName());
    }

    @Test
    public void testLoad126() throws Exception {

        XMLConfiguration config = loadConfig("geowebcache_126.xml");

        ServiceInformation serviceInfo = config.getServiceInformation();
        assertNotNull(serviceInfo);
        assertEquals("GeoWebCache", serviceInfo.getTitle());
        assertEquals("GeoWebCache description.", serviceInfo.getDescription());

        // check transform from <keyowrds><keyword>... to <keywords><string>...
        assertEquals(asList("WFS", "WMS", "WMTS", "GEOWEBCACHE"), serviceInfo.getKeywords());

        assertNotNull(serviceInfo.getAccessConstraints());
        assertNotNull(serviceInfo.getFees());
        assertNotNull(serviceInfo.getServiceProvider());
        assertNotNull(serviceInfo.getServiceProvider().getProviderName());
        assertNotNull(serviceInfo.getServiceProvider().getProviderSite());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getAddressAdministrativeArea());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getAddressCity());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getAddressCountry());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getAddressEmail());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getAddressPostalCode());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getAddressStreet());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getAddressType());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getFaxNumber());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getIndividualName());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getPhoneNumber());
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact().getPositionName());

        Iterable<TileLayer> layers = config.getLayers();
        TileLayer layer = findLayer(layers, "topp:states");
        assertNotNull(layer);

        assertEquals(4, layer.getMimeTypes().size());
        assertTrue(layer.getGridSubsets().contains("EPSG:2163"));
    }

    @Test
    public void testUpgradedConfigurationsValidate() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String fileName : UPGRADE_FILES) {
            try (InputStream is = XMLConfiguration.class.getResourceAsStream(fileName)) {
                XMLConfiguration.validate(XMLConfiguration.loadDocument(is));
            } catch (SAXException e) {
                failures.add(fileName + ": " + e.getMessage());
            }
        }
        assertEquals(String.join("\n", failures), 0, failures.size());
    }

    private TileLayer findLayer(Iterable<TileLayer> layers, String layerName) throws GeoWebCacheException {
        Iterator<TileLayer> iter = layers.iterator();

        int i = 0;
        while (iter.hasNext()) {
            TileLayer layer = iter.next();
            if (layer.getName().equals(layerName)) {
                return layer;
            }
            i++;
        }

        throw new GeoWebCacheException("Layer " + layerName + " not found, set has " + i + " layers.");
    }

    private Iterable<TileLayer> loadResource(String fileName) throws Exception {
        return loadConfig(fileName).getLayers();
    }

    private XMLConfiguration loadConfig(String fileName) throws Exception {

        // try (InputStream is = XMLConfiguration.class.getResourceAsStream(fileName); ) {
        //     Node root = XMLConfiguration.loadDocument(is);
        //     print(root.getOwnerDocument());
        // }

        XMLConfiguration xmlConfig = new XMLConfiguration(
                null,
                new MockConfigurationResourceProvider(() -> XMLConfiguration.class.getResourceAsStream(fileName)));

        GridSetBroker gsb = new GridSetBroker(Arrays.asList(new DefaultGridsets(true, true), xmlConfig));
        xmlConfig.setGridSetBroker(gsb);
        xmlConfig.afterPropertiesSet();

        Iterator<TileLayer> iter = xmlConfig.getLayers().iterator();
        while (iter.hasNext()) {
            TileLayer layer = iter.next();

            layer.initialize(gsb);
        }

        return xmlConfig;
    }

    /** Utility method to print out a dom. */
    protected void print(Document dom) throws Exception {
        TransformerFactory txFactory = XMLUtils.newTransformerFactory();
        try {
            txFactory.setAttribute("{http://xml.apache.org/xalan}indent-number", Integer.valueOf(2));
        } catch (Exception e) {
            // some
        }

        Transformer tx = XMLUtils.newTransformer(txFactory);
        tx.setOutputProperty(OutputKeys.METHOD, "xml");
        tx.setOutputProperty(OutputKeys.INDENT, "yes");

        tx.transform(new DOMSource(dom), new StreamResult(new OutputStreamWriter(System.out, UTF_8)));
    }
}
