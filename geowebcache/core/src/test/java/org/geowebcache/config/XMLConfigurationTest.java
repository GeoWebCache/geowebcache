package org.geowebcache.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.xml.sax.SAXParseException;

public class XMLConfigurationTest extends TestCase {

    private static final Log log = LogFactory.getLog(XMLConfigurationTest.class);

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

        gridSetBroker = new GridSetBroker(true, true);
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
            fail("Expected IllegalArgumentException on duplicate layer name");
        } catch (IllegalArgumentException e) {
            assertEquals("Layer 'testLayer' already exists", e.getMessage());
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
        Map<String, GridSubset> subSets = new HashMap<String, GridSubset>();
        GridSubset gridSubSet = GridSubsetFactory.createGridSubSet(gridSetBroker.get("EPSG:4326"));
        subSets.put(gridSubSet.getName(), gridSubSet);

        StringParameterFilter filter = new StringParameterFilter();
        filter.setKey("STYLES");
        filter.getValues().addAll(Arrays.asList("polygon", "point"));
        filter.setDefaultValue("polygon");

        List<ParameterFilter> parameterFilters = new ArrayList<ParameterFilter>(
                new ArrayList<ParameterFilter>(Arrays.asList((ParameterFilter) filter)));
        int[] metaWidthHeight = { 9, 9 };
        String vendorParams = "vendor=1";
        boolean queryable = false;

        WMSLayer layer = new WMSLayer(layerName, wmsURL, wmsStyles, wmsLayers, mimeFormats,
                subSets, parameterFilters, metaWidthHeight, vendorParams, queryable);

        config.addLayer(layer);

        config.save();

        IOUtils.copy(new FileInputStream(configFile), System.out);
        try {
            XMLConfiguration.validate(XMLConfiguration
                    .loadDocument(new FileInputStream(configFile)));
        } catch (SAXParseException e) {
            log.error(e.getMessage());
            fail(e.getMessage());
        }

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
        for (GridSubset expected : subSets.values()) {
            GridSubset actual = l.getGridSubset(expected.getName());
            assertNotNull(actual);
            assertEquals(new XMLGridSubset(expected), new XMLGridSubset(actual));
        }
    }

    public void testSaveGridSet() throws Exception {
        String name = "testGrid";
        SRS srs = SRS.getEPSG4326();
        BoundingBox extent = new BoundingBox(-1, -1, 1, 1);
        boolean alignTopLeft = true;
        double[] resolutions = { 3, 2, 1 };
        double[] scaleDenoms = null;
        Double metersPerUnit = 1.5;
        double pixelSize = 2 * GridSetFactory.DEFAULT_PIXEL_SIZE_METER;
        String[] scaleNames = { "uno", "dos", "tres" };
        int tileWidth = 128;
        int tileHeight = 512;
        boolean yCoordinateFirst = true;

        GridSet gridSet = GridSetFactory.createGridSet(name, srs, extent, alignTopLeft,
                resolutions, scaleDenoms, metersPerUnit, pixelSize, scaleNames, tileWidth,
                tileHeight, yCoordinateFirst);
        gridSet.setDescription("test description");

        config.addOrReplaceGridSet(new XMLGridSet(gridSet));
        config.save();

        IOUtils.copy(new FileInputStream(configFile), System.out);
        try {
            XMLConfiguration.validate(XMLConfiguration
                    .loadDocument(new FileInputStream(configFile)));
        } catch (SAXParseException e) {
            log.error(e.getMessage());
            fail(e.getMessage());
        }

        XMLConfiguration config2 = new XMLConfiguration(null, configDir.getAbsolutePath());
        GridSetBroker gridSetBroker2 = new GridSetBroker(true, false);
        config2.initialize(gridSetBroker2);

        GridSet gridSet2 = gridSetBroker2.get(name);
        assertNotNull(gridSet2);
        assertEquals(gridSet, gridSet2);
    }

    public void testSaveCurrentVersion() throws Exception {

        URL source = XMLConfiguration.class
                .getResource(XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE);
        configFile = new File(configDir, "geowebcache.xml");
        FileUtils.copyURLToFile(source, configFile);

        gridSetBroker = new GridSetBroker(true, false);
        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.initialize(gridSetBroker);

        final String previousVersion = config.getVersion();
        assertNotNull(previousVersion);

        config.save();

        final String currVersion = XMLConfiguration.getCurrentSchemaVersion();
        assertNotNull(currVersion);
        assertFalse(previousVersion.equals(currVersion));

        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.initialize(gridSetBroker);
        final String savedVersion = config.getVersion();
        assertEquals(currVersion, savedVersion);
    }
}
