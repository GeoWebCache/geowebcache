package org.geowebcache.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;

public class XMLConfigurationTest extends TestCase {

    private File configDir;

    private File configFile;

    private GridSetBroker gridSetBroker;

    private XMLConfiguration config;

    protected void setUp() throws Exception {
        configDir = new File("target", "testConfig");
        FileUtils.deleteDirectory(configDir);
        configDir.mkdirs();
        URL source = XMLConfiguration.class
                .getResource(XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
        configFile = new File(configDir, "geowebcache.xml");
        FileUtils.copyURLToFile(source, configFile);

        gridSetBroker = new GridSetBroker(true, false);
        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.initialize(gridSetBroker);
    }

    public void testAddLayer() throws Exception {
        int count = config.getTileLayerCount();

        TileLayer tl = mock(WMSLayer.class);
        when(tl.getName()).thenReturn("testLayer");
        config.addLayer(tl);
        assertEquals(count + 1, config.getTileLayerCount());
        assertSame(tl, config.getTileLayer("testLayer"));
        try {
            config.addLayer(tl);
            fail("Expected GeoWebCacheException on duplicate layer name");
        } catch (GeoWebCacheException e) {
            assertEquals("Layer testLayer already exists", e.getMessage());
        }
    }

    public void testModifyLayer() throws Exception {

        TileLayer layer1 = mock(WMSLayer.class);
        when(layer1.getName()).thenReturn("testLayer");

        config.addLayer(layer1);
        int count = config.getTileLayerCount();

        TileLayer layer2 = mock(WMSLayer.class);
        when(layer2.getName()).thenReturn("testLayer");
        config.modifyLayer(layer2);

        assertEquals(count, config.getTileLayerCount());
        assertSame(layer2, config.getTileLayer("testLayer"));

        when(layer1.getName()).thenReturn("another");
        try {
            config.modifyLayer(layer1);
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }

    public void testRemoveLayer() {

        assertFalse(config.removeLayer("nonExistent"));

        Set<String> tileLayerNames = config.getTileLayerNames();
        for (String name : tileLayerNames) {
            int count = config.getTileLayerCount();
            assertTrue(config.removeLayer(name));
            assertEquals(count - 1, config.getTileLayerCount());
        }
    }

    public void testTemplate() throws Exception {
        assertTrue(configFile.delete());
        config.setTemplate("/geowebcache_empty.xml");
        config.initialize(gridSetBroker);
        assertEquals(0, config.getTileLayerCount());

        assertTrue(configFile.delete());
        config.setTemplate("/geowebcache.xml");
        config.initialize(gridSetBroker);
        assertEquals(3, config.getTileLayerCount());
    }

    public void testSave() throws Exception {
        for (String name : config.getTileLayerNames()) {
            int count = config.getTileLayerCount();
            assertTrue(config.removeLayer(name));
            assertEquals(count - 1, config.getTileLayerCount());
        }

        String layerName = "testLayer";
        String[] wmsURL = { "http://wms.example.com/1", "http://wms.example.com/2" };
        String wmsStyles = "default,line";
        String wmsLayers = "states,border";
        List<String> mimeFormats = Arrays.asList("image/png", "image/jpeg");
        Hashtable<String, GridSubset> subSets = null;

        StringParameterFilter filter = new StringParameterFilter();
        filter.key = "STYLES";
        filter.values = new ArrayList<String>(Arrays.asList("polygon", "point"));
        filter.defaultValue = "polygon";

        List<ParameterFilter> parameterFilters = new ArrayList<ParameterFilter>(
                new ArrayList<ParameterFilter>(Arrays.asList((ParameterFilter) filter)));
        int[] metaWidthHeight = { 9, 9 };
        String vendorParams = "vendor=1";
        boolean queryable = false;

        WMSLayer layer = new WMSLayer(layerName, wmsURL, wmsStyles, wmsLayers, mimeFormats,
                subSets, parameterFilters, metaWidthHeight, vendorParams, queryable);

        config.addLayer(layer);

        config.save();

        XMLConfiguration config2 = new XMLConfiguration(null, configDir.getAbsolutePath());
        config2.initialize(gridSetBroker);
        assertEquals(1, config2.getTileLayerCount());
        assertNotNull(config2.getTileLayer("testLayer"));

        WMSLayer l = (WMSLayer) config2.getTileLayer("testLayer");
        assertTrue(Arrays.equals(wmsURL, l.getWMSurl()));
        assertEquals(wmsStyles, l.getStyles());
        assertEquals(wmsLayers, l.getWmsLayers());
        assertEquals(mimeFormats, l.getMimeFormats());
        assertEquals(parameterFilters, l.getParameterFilters());
    }

}
