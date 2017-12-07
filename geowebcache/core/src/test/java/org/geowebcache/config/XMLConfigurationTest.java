package org.geowebcache.config;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.*;

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

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.legends.LegendRawInfo;
import org.geowebcache.config.legends.LegendsRawInfo;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXParseException;

public class XMLConfigurationTest {

    private static final Log log = LogFactory.getLog(XMLConfigurationTest.class);

    private File configDir;

    private File configFile;

    private GridSetBroker gridSetBroker;

    private XMLConfiguration config;
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    @Before
    public void setUp() throws Exception {
        configDir = temp.getRoot();
        configFile = temp.newFile("geowebcache.xml");
        
        URL source = XMLConfiguration.class
                .getResource(XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
        FileUtils.copyURLToFile(source, configFile);

        gridSetBroker = new GridSetBroker(true, true);
        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.initialize(gridSetBroker);
    }
    
    @Test
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

    @Test
    public void testNotAddLayer() throws Exception {
        // Create a transient Layer and check if it can be accepted
        TileLayer tl = mock(WMSLayer.class);
        when(tl.getName()).thenReturn("testLayer");
        when(tl.isTransientLayer()).thenReturn(true);
        assertFalse(config.canSave(tl));
    }

    @Test
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

    @Test
    public void testRemoveLayer() {

        assertFalse(config.removeLayer("nonExistent"));

        Set<String> tileLayerNames = config.getTileLayerNames();
        for (String name : tileLayerNames) {
            int count = config.getTileLayerCount();
            assertTrue(config.removeLayer(name));
            assertEquals(count - 1, config.getTileLayerCount());
        }
    }

    @Test
    public void testTemplate() throws Exception {
        assertTrue(configFile.delete());
        config.setTemplate("/geowebcache_empty.xml");
        config.initialize(gridSetBroker);
        assertEquals(0, config.getTileLayerCount());

        assertTrue(configFile.delete());
        config.setTemplate("/geowebcache.xml");
        config.initialize(gridSetBroker);
        assertEquals(3, config.getTileLayerCount());
        // WMTS CITE strict compliance should be deactivated
        assertThat(config.isWmtsCiteCompliant(), is(false));
    }

    @Test
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
        filter.setValues(Arrays.asList("polygon", "point"));
        filter.setDefaultValue("polygon");

        List<ParameterFilter> parameterFilters = new ArrayList<ParameterFilter>(
                new ArrayList<ParameterFilter>(Arrays.asList((ParameterFilter) filter)));
        int[] metaWidthHeight = { 9, 9 };
        String vendorParams = "vendor=1";
        boolean queryable = false;
        String wmsQueryLayers = null;

        WMSLayer layer = new WMSLayer(layerName, wmsURL, wmsStyles, wmsLayers, mimeFormats,
                subSets, parameterFilters, metaWidthHeight, vendorParams, queryable, wmsQueryLayers);

        // create legends information
        LegendsRawInfo legendsRawInfo = new LegendsRawInfo();
        legendsRawInfo.setDefaultWidth(50);
        legendsRawInfo.setDefaultHeight(100);
        legendsRawInfo.setDefaultFormat("image/png");
        // legend with all values and custom url
        LegendRawInfo legendRawInfoA = new LegendRawInfo();
        legendRawInfoA.setStyle("polygon");
        legendRawInfoA.setWidth(75);
        legendRawInfoA.setHeight(125);
        legendRawInfoA.setFormat("image/jpeg");
        legendRawInfoA.setUrl("http://url");
        legendRawInfoA.setMinScale(5000D);
        legendRawInfoA.setMaxScale(10000D);

        // legend with a complete url
        LegendRawInfo legendRawInfoB = new LegendRawInfo();
        legendRawInfoB.setStyle("point");
        legendRawInfoB.setCompleteUrl("http://url");
        // default style legend
        LegendRawInfo legendRawInfoC = new LegendRawInfo();
        legendRawInfoC.setStyle("");
        // tie the legend information together
        legendsRawInfo.addLegendRawInfo(legendRawInfoA);
        legendsRawInfo.addLegendRawInfo(legendRawInfoB);
        legendsRawInfo.addLegendRawInfo(legendRawInfoC);
        layer.setLegends(legendsRawInfo);

        config.addLayer(layer);
        config.save();

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

        // check legends info
        assertThat(l.getLegends(), notNullValue());
        assertThat(l.getLegends().getDefaultWidth(), is(50));
        assertThat(l.getLegends().getDefaultHeight(), is(100));
        assertThat(l.getLegends().getDefaultFormat(), is("image/png"));
        assertThat(l.getLegends().getLegendsRawInfo().size(), is(3));
        assertThat(l.getLegends().getLegendsRawInfo(), containsInAnyOrder(legendRawInfoA, legendRawInfoB, legendRawInfoC));
    }

    @Test
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
    
    @Test
    public void testNoBlobStores() throws Exception{
        assertNotNull(config.getBlobStores());
        assertTrue(config.getBlobStores().isEmpty());
    }

    @Test
    public void testSaveBlobStores() throws Exception{
        FileBlobStoreConfig store1 = new FileBlobStoreConfig();
        store1.setId("store1");
        store1.setDefault(true);
        store1.setEnabled(true);
        store1.setFileSystemBlockSize(8096);
        store1.setBaseDirectory("/tmp/test");
        
        FileBlobStoreConfig store2 = new FileBlobStoreConfig();
        store2.setId("store2");
        store2.setDefault(false);
        store2.setEnabled(false);
        store2.setFileSystemBlockSize(512);
        store2.setBaseDirectory("/tmp/test2");

        config.getBlobStores().add(store1);
        config.getBlobStores().add(store2);
        config.save();

        try {
            XMLConfiguration.validate(XMLConfiguration
                    .loadDocument(new FileInputStream(configFile)));
        } catch (SAXParseException e) {
            log.error(e.getMessage());
            fail(e.getMessage());
        }

        XMLConfiguration config2 = new XMLConfiguration(null, configDir.getAbsolutePath());
        config2.initialize(new GridSetBroker(true, false));
        
        List<BlobStoreConfig> stores = config2.getBlobStores();
        assertNotNull(stores);
        assertEquals(2, stores.size());
        assertNotSame(store1, stores.get(0));
        assertEquals(store1, stores.get(0));

        assertNotSame(store2, stores.get(1));
        assertEquals(store2, stores.get(1));
    }

    @Test
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

    @Test
    public void testWmtsCiteStrictComplianceIsActivated() throws Exception {
        // delete existing GWC configuration file
        assertThat(configFile.delete(), is(true));
        // instantiate a new one based with strict CITE compliance activated
        config.setTemplate("/geowebcache_cite.xml");
        config.initialize(gridSetBroker);
        // CITE strict compliance should be activated for WMTS
        assertThat(config.isWmtsCiteCompliant(), is(true));
    }
}
