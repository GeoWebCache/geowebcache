package org.geowebcache.config;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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
import org.w3c.dom.Node;

public class XMLConfigurationBackwardsCompatibilityTest {

    public static final String GWC_125_CONFIG_FILE = "geowebcache_125.xml";

    public static final String LATEST_FILENAME = "geowebcache_130.xml";

    @Test
    public void testLoadPre10() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_pre10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
    }

    @Test
    public void testLoad10() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        // assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
    }

    @Test
    public void testLoad101() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_101.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        // assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);

        // The additions in 1.0.1 are allowCacheBypass and backendTimeout
        assertEquals(layer.getBackendTimeout().intValue(), 60);
        assertEquals(layer2.getBackendTimeout().intValue(), 235);
        assertEquals(layer.isCacheBypassAllowed().booleanValue(), true);
        assertEquals(layer2.isCacheBypassAllowed().booleanValue(), false);
    }

    @Test
    public void testLoad114() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_114.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        // assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);

        // The additions in 1.0.1 are allowCacheBypass and backendTimeout
        assertEquals(layer.getBackendTimeout().intValue(), 120);
        assertEquals(layer2.getBackendTimeout().intValue(), 120);
        assertEquals(layer.isCacheBypassAllowed().booleanValue(), true);
        assertEquals(layer2.isCacheBypassAllowed().booleanValue(), true);

        FormatModifier fm = layer.getFormatModifier(ImageMime.jpeg);
        assertEquals(fm.getBgColor(), "0xDDDDDD");
        assertTrue(fm.getRequestFormat().equals(ImageMime.png));

        List<RequestFilter> filters = layer.getRequestFilters();
        assertEquals(filters.get(0).getName(), "testWMSRasterFilter");
        assertEquals(filters.get(1).getName(), "testFileRasterFilter");
    }

    @Test
    public void testLoad115() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_115.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        // assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubset grid = layer2.getGridSubsetForSRS(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);

        // The additions in 1.0.1 are allowCacheBypass and backendTimeout
        assertEquals(layer.getBackendTimeout().intValue(), 120);
        assertEquals(layer2.getBackendTimeout().intValue(), 120);
        assertEquals(layer.isCacheBypassAllowed().booleanValue(), true);
        assertEquals(layer2.isCacheBypassAllowed().booleanValue(), true);

        FormatModifier fm = layer.getFormatModifier(ImageMime.jpeg);
        assertEquals(fm.getBgColor(), "0xDDDDDD");
        assertTrue(fm.getRequestFormat().equals(ImageMime.png));

        List<RequestFilter> filters = layer.getRequestFilters();
        RequestFilter filter0 = filters.get(0);
        assertEquals(filter0.getName(), "testWMSRasterFilter");
        RequestFilter filter1 = filters.get(1);
        assertEquals(filter1.getName(), "testFileRasterFilter");
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
        assertNotNull(serviceInfo.getServiceProvider().getServiceContact()
                .getAddressAdministrativeArea());
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

        List<TileLayer> layers = config.getTileLayers();
        TileLayer layer = findLayer(layers, "topp:states");
        assertNotNull(layer);

        assertEquals(4, layer.getMimeTypes().size());
        assertTrue(layer.getGridSubsets().contains("EPSG:2163"));
    }

    private TileLayer findLayer(List<TileLayer> layers, String layerName)
            throws GeoWebCacheException {
        Iterator<TileLayer> iter = layers.iterator();

        while (iter.hasNext()) {
            TileLayer layer = iter.next();
            if (layer.getName().equals(layerName)) {
                return layer;
            }
        }

        throw new GeoWebCacheException("Layer " + layerName + " not found, set has "
                + layers.size() + " layers.");
    }

    private List<TileLayer> loadResource(String fileName) throws Exception {
        return loadConfig(fileName).getTileLayers();
    }

    private XMLConfiguration loadConfig(String fileName) throws Exception {

        InputStream is;

        is = XMLConfiguration.class.getResourceAsStream(fileName);
        try {
            Node root = XMLConfiguration.loadDocument(is);
            print(root.getOwnerDocument());
        } finally {
            is.close();
        }

        is = XMLConfiguration.class.getResourceAsStream(fileName);

        XMLConfiguration xmlConfig = new XMLConfiguration(is);

        GridSetBroker gsb = new GridSetBroker(false, false);
        xmlConfig.initialize(gsb);

        List<TileLayer> list = xmlConfig.getTileLayers();

        Iterator<TileLayer> iter = list.iterator();
        while (iter.hasNext()) {
            TileLayer layer = iter.next();

            layer.initialize(gsb);
        }

        return xmlConfig;
    }

    /**
     * Utility method to print out a dom.
     */
    protected void print(Document dom) throws Exception {
        TransformerFactory txFactory = TransformerFactory.newInstance();
        try {
            txFactory.setAttribute("{http://xml.apache.org/xalan}indent-number", new Integer(2));
        } catch (Exception e) {
            // some
        }

        Transformer tx = txFactory.newTransformer();
        tx.setOutputProperty(OutputKeys.METHOD, "xml");
        tx.setOutputProperty(OutputKeys.INDENT, "yes");

        tx.transform(new DOMSource(dom), new StreamResult(new OutputStreamWriter(System.out,
                "utf-8")));
    }
}