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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.config.legends.LegendRawInfo;
import org.geowebcache.config.legends.LegendsRawInfo;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.storage.UnsuitableStorageException;
import org.geowebcache.util.TestUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.xml.sax.SAXParseException;

public class XMLConfigurationTest {

    private static final Logger log = Logging.getLogger(XMLConfigurationTest.class.getName());

    private File configDir;

    private File configFile;

    private GridSetBroker gridSetBroker;

    private XMLConfiguration config;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule // Not actually used but it protects against other tests that clutter the extension system
    public MockWepAppContextRule contextRule = new MockWepAppContextRule();

    @Before
    public void setUp() throws Exception {
        configDir = temp.getRoot();
        configFile = temp.newFile("geowebcache.xml");

        URL source = XMLConfiguration.class.getResource(XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
        FileUtils.copyURLToFile(source, configFile);

        gridSetBroker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true)));
        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.setGridSetBroker(gridSetBroker);
        config.afterPropertiesSet();
    }

    @Test
    public void testAddLayer() throws Exception {
        int count = config.getLayerCount();

        TileLayer tl = createTestLayer("testLayer");
        config.addLayer(tl);
        assertEquals(count + 1, config.getLayerCount());
        assertSame(tl, config.getLayer("testLayer").get());
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

        WMSLayer layer1 = createTestLayer("testLayer");

        config.addLayer(layer1);
        int count = config.getLayerCount();

        WMSLayer layer2 = createTestLayer("testLayer");
        config.modifyLayer(layer2);

        assertEquals(count, config.getLayerCount());
        assertSame(layer2, config.getLayer("testLayer").get());

        layer1 = createTestLayer("another");
        try {
            config.modifyLayer(layer1);
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testRemoveLayer() {

        try {
            config.removeLayer("nonExistent");
            fail("Expected exception removing nonExistant layer");
        } catch (Exception e) {
        }

        Set<String> tileLayerNames = config.getLayerNames();
        for (String name : tileLayerNames) {
            int count = config.getLayerCount();
            config.removeLayer(name);
            assertEquals(count - 1, config.getLayerCount());
        }
    }

    @Test
    public void testTemplate() throws Exception {
        assertTrue(configFile.delete());
        config.setTemplate("/geowebcache_empty.xml");
        config.setGridSetBroker(gridSetBroker);
        config.deinitialize();
        config.reinitialize();
        config.getLayerCount();
        assertEquals(0, config.getLayerCount());

        assertTrue(configFile.delete());
        config.setTemplate("/geowebcache.xml");
        config.setGridSetBroker(gridSetBroker);
        config.deinitialize();
        config.reinitialize();
        config.getLayerCount();
        assertEquals(3, config.getLayerCount());
        // WMTS CITE strict compliance should be deactivated
        assertThat(config.isWmtsCiteCompliant(), is(false));
    }

    @Test
    public void testSave() throws Exception {
        for (String name : config.getLayerNames()) {
            int count = config.getLayerCount();
            config.removeLayer(name);
            assertEquals(count - 1, config.getLayerCount());
        }

        String layerName = "testLayer";
        String[] wmsURL = {"http://wms.example.com/1", "http://wms.example.com/2"};
        String wmsStyles = "default,line";
        String wmsLayers = "states,border";
        List<String> mimeFormats = Arrays.asList("image/png", "image/jpeg");
        Map<String, GridSubset> subSets = new HashMap<>();
        GridSubset gridSubSet = GridSubsetFactory.createGridSubSet(gridSetBroker.get("EPSG:4326"));
        subSets.put(gridSubSet.getName(), gridSubSet);

        StringParameterFilter filter = new StringParameterFilter();
        filter.setKey("STYLES");
        filter.setValues(Arrays.asList("polygon", "point"));
        filter.setDefaultValue("polygon");

        List<ParameterFilter> parameterFilters =
                new ArrayList<>(new ArrayList<>(Arrays.asList((ParameterFilter) filter)));
        int[] metaWidthHeight = {9, 9};
        String vendorParams = "vendor=1";
        boolean queryable = false;
        String wmsQueryLayers = null;

        WMSLayer layer = new WMSLayer(
                layerName,
                wmsURL,
                wmsStyles,
                wmsLayers,
                mimeFormats,
                subSets,
                parameterFilters,
                metaWidthHeight,
                vendorParams,
                queryable,
                wmsQueryLayers);

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

        try {
            XMLConfiguration.validate(XMLConfiguration.loadDocument(new FileInputStream(configFile)));
        } catch (SAXParseException e) {
            log.log(Level.SEVERE, e.getMessage());
            fail(e.getMessage());
        }

        XMLConfiguration config2 = new XMLConfiguration(null, configDir.getAbsolutePath());
        config2.setGridSetBroker(gridSetBroker);
        config2.afterPropertiesSet();
        config2.getLayerCount();
        assertEquals(1, config2.getLayerCount());
        assertThat(config2.getLayer("testLayer"), TestUtils.isPresent());

        WMSLayer l = (WMSLayer) config2.getLayer("testLayer").get();
        assertArrayEquals(wmsURL, l.getWMSurl());
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
        assertThat(
                l.getLegends().getLegendsRawInfo(), containsInAnyOrder(legendRawInfoA, legendRawInfoB, legendRawInfoC));
    }

    public WMSLayer createTestLayer(String layerName) {
        String[] wmsURL = {"http://wms.example.com/1", "http://wms.example.com/2"};
        String wmsStyles = "default,line";
        String wmsLayers = "states,border";
        List<String> mimeFormats = Arrays.asList("image/png", "image/jpeg");
        Map<String, GridSubset> subSets = new HashMap<>();
        GridSubset gridSubSet = GridSubsetFactory.createGridSubSet(gridSetBroker.get("EPSG:4326"));
        subSets.put(gridSubSet.getName(), gridSubSet);

        StringParameterFilter filter = new StringParameterFilter();
        filter.setKey("STYLES");
        filter.setValues(Arrays.asList("polygon", "point"));
        filter.setDefaultValue("polygon");

        List<ParameterFilter> parameterFilters =
                new ArrayList<>(new ArrayList<>(Arrays.asList((ParameterFilter) filter)));
        int[] metaWidthHeight = {9, 9};
        String vendorParams = "vendor=1";
        boolean queryable = false;
        String wmsQueryLayers = null;

        WMSLayer layer = new WMSLayer(
                layerName,
                wmsURL,
                wmsStyles,
                wmsLayers,
                mimeFormats,
                subSets,
                parameterFilters,
                metaWidthHeight,
                vendorParams,
                queryable,
                wmsQueryLayers);

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

        return layer;
    }

    @Test
    public void testSaveGridSet() throws Exception {
        String name = "testGrid";
        SRS srs = SRS.getEPSG4326();
        BoundingBox extent = new BoundingBox(-1, -1, 1, 1);
        boolean alignTopLeft = true;
        double[] resolutions = {3, 2, 1};
        double[] scaleDenoms = null;
        Double metersPerUnit = 1.5;
        double pixelSize = 2 * GridSetFactory.DEFAULT_PIXEL_SIZE_METER;
        String[] scaleNames = {"uno", "dos", "tres"};
        int tileWidth = 128;
        int tileHeight = 512;
        boolean yCoordinateFirst = true;

        GridSet gridSet = GridSetFactory.createGridSet(
                name,
                srs,
                extent,
                alignTopLeft,
                resolutions,
                scaleDenoms,
                metersPerUnit,
                pixelSize,
                scaleNames,
                tileWidth,
                tileHeight,
                yCoordinateFirst);
        gridSet.setDescription("test description");

        config.addGridSet(gridSet);

        try {
            XMLConfiguration.validate(XMLConfiguration.loadDocument(new FileInputStream(configFile)));
        } catch (SAXParseException e) {
            log.log(Level.SEVERE, e.getMessage());
            fail(e.getMessage());
        }

        XMLConfiguration config2 = new XMLConfiguration(null, configDir.getAbsolutePath());
        GridSetBroker gridSetBroker2 = new GridSetBroker(Arrays.asList(new DefaultGridsets(true, true), config2));
        config2.setGridSetBroker(gridSetBroker2);
        config2.afterPropertiesSet();
        config2.getLayerCount();

        GridSet gridSet2 = gridSetBroker2.get(name);
        assertNotNull(gridSet2);
        assertEquals(gridSet, gridSet2);
    }

    @Test
    public void testOverrideGridSetDefaults() throws Exception {
        // overwrite the config file with one that has a non-default definition of EPSG:4326
        URL source = XMLConfiguration.class.getResource("geowebcache_4326_override.xml");
        FileUtils.copyURLToFile(source, configFile);
        // get a new XMLConfiguration for the override
        XMLConfiguration config2 = new XMLConfiguration(null, configDir.getAbsolutePath());
        // create the broker with the Defaults first.
        final DefaultGridsets defaultGridSets = new DefaultGridsets(true, true);
        gridSetBroker = new GridSetBroker(Arrays.asList(defaultGridSets));
        config2.setGridSetBroker(gridSetBroker);
        // mock out an app context so we can get extension priorities working
        ApplicationContext appContext = createMock(ApplicationContext.class);
        final HashMap<String, GridSetConfiguration> beans = new HashMap<>(2);
        beans.put("defaultGridSets", defaultGridSets);
        beans.put("xmlConfig", config2);
        expect(appContext.getBeansOfType(GridSetConfiguration.class)).andReturn(beans);
        expect(appContext.getBean("defaultGridSets")).andReturn(defaultGridSets);
        expect(appContext.getBean("xmlConfig")).andReturn(config2);
        replay(appContext);
        // registering our mocked spring application context
        GeoWebCacheExtensions gwcExtensions = new GeoWebCacheExtensions();
        gwcExtensions.setApplicationContext(appContext);
        // get the GridSet for 4326, should be the override in the test XML file
        GridSet override4326 = gridSetBroker.get("EPSG:4326");
        assertNotNull(override4326);
        // make sure GridSetBroker returns the overriden 4326 when getWorld4326() is used, so that
        // the same 4326 is used
        // when asking by name, or convenience method
        GridSet worldEpsg4326 = gridSetBroker.getWorldEpsg4326();
        assertNotNull(worldEpsg4326);
        assertEquals(override4326, worldEpsg4326);
        // get the internal default GridSet for 4326.
        GridSet internal4326 = defaultGridSets.worldEpsg4326();
        // override should have a different resolution list
        assertEquals("Unexpected number of Default EPSG:4326 resolution levels", 22, internal4326.getNumLevels());
        assertEquals("Unexpected number of Overriden EPSG:4326 resolution levels", 14, override4326.getNumLevels());
        // first level on override should be 1.40625
        final Grid overrideLevel = override4326.getGrid(0);
        final Grid defaultLevel = internal4326.getGrid(0);
        assertEquals("Unexpected default resolution level 0", 0.703125, defaultLevel.getResolution(), 0d);
        assertEquals("Unexpected override resolution level 0", 1.40625, overrideLevel.getResolution(), 0d);
        // ensure descriptions are expected
        final String overrideDescription = override4326.getDescription();
        final String defaultDescription = internal4326.getDescription();
        assertFalse(
                "Default EPSG:4326 GridSet description should not contain 'OVERRIDE'",
                defaultDescription.contains("OVERRIDE"));
        assertTrue(
                "Overriden EPSG:4326 GridSet description should contain 'OVERRIDE'",
                overrideDescription.contains("OVERRIDE"));
    }

    @Test
    public void testNoBlobStores() throws Exception {
        assertNotNull(config.getBlobStores());
        assertTrue(config.getBlobStores().isEmpty());
    }

    @Test
    public void testAddBlobStores() throws Exception {
        FileBlobStoreInfo store1 = new FileBlobStoreInfo();
        store1.setName("store1");
        store1.setDefault(true);
        store1.setEnabled(true);
        store1.setFileSystemBlockSize(8096);
        store1.setBaseDirectory("/tmp/test");

        FileBlobStoreInfo store2 = new FileBlobStoreInfo();
        store2.setName("store2");
        store2.setDefault(false);
        store2.setEnabled(false);
        store2.setFileSystemBlockSize(512);
        store2.setBaseDirectory("/tmp/test2");

        config.addBlobStore(store1);
        config.addBlobStore(store2);

        try {
            XMLConfiguration.validate(XMLConfiguration.loadDocument(new FileInputStream(configFile)));
        } catch (SAXParseException e) {
            log.log(Level.SEVERE, e.getMessage());
            fail(e.getMessage());
        }

        XMLConfiguration config2 = new XMLConfiguration(null, configDir.getAbsolutePath());
        config2.setGridSetBroker(new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true))));
        config2.afterPropertiesSet();
        config2.getLayerCount();

        List<BlobStoreInfo> stores = config2.getBlobStores();
        assertNotNull(stores);
        assertEquals(2, stores.size());
        assertNotSame(store1, stores.get(0));
        assertEquals(store1, stores.get(0));

        assertNotSame(store2, stores.get(1));
        assertEquals(store2, stores.get(1));
    }

    @Test
    public void testAddBlobStoreExceptionSaving() throws Exception {

        XMLFileResourceProvider resourceProvider =
                new XMLFileResourceProvider(
                        XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME,
                        (WebApplicationContext) null,
                        this.configDir.getAbsolutePath(),
                        null) {

                    // throw an ioexception the first time close() is called, the second time is the roll-back
                    private boolean thrown = false;

                    @Override
                    public OutputStream out() throws IOException {
                        OutputStream real = super.out();
                        return new FilterOutputStream(real) {

                            @Override
                            public void close() throws IOException {
                                real.close();
                                if (thrown) {
                                    return;
                                }
                                thrown = true;
                                throw new IOException("forced io exception");
                            }
                        };
                    }
                };

        config = new XMLConfiguration(null, resourceProvider);
        config.setGridSetBroker(gridSetBroker);

        FileBlobStoreInfo store1 = new FileBlobStoreInfo();
        store1.setName("store1");
        store1.setDefault(true);
        store1.setEnabled(true);
        store1.setFileSystemBlockSize(8096);
        store1.setBaseDirectory("/tmp/test");

        assertThrows(ConfigurationPersistenceException.class, () -> config.addBlobStore(store1));
        assertEquals(0, config.getBlobStoreCount());
        GeoWebCacheConfiguration configuration = config.loadConfiguration();
        assertTrue("store shouldn't be saved", configuration.getBlobStores().isEmpty());
    }

    /**
     * Verifies the blobstore configuration is rolled back from the persisted configuration if a
     * {@link BlobStoreConfigurationListener#handleAddBlobStore} throws an {@link UnsuitableStorageException}.
     *
     * <p>Note I'm not sure why XMLConfiguration.addBlobStore() only rolls-back on UnsuitableStorageException and not on
     * IOException or GeoWebCacheException
     */
    @Test
    public void testAddBlobStoreExceptionFromListener() throws Exception {
        FileBlobStoreInfo store1 = new FileBlobStoreInfo();
        store1.setName("store1");
        store1.setDefault(true);
        store1.setEnabled(true);
        store1.setFileSystemBlockSize(8096);
        store1.setBaseDirectory("/tmp/test");

        BlobStoreConfigurationListener listener;

        listener = mock(BlobStoreConfigurationListener.class);
        doThrow(new UnsuitableStorageException("fake")).when(listener).handleAddBlobStore(Mockito.any());
        config.addBlobStoreListener(listener);

        assertAddBlobStoreFails(store1, UnsuitableStorageException.class);

        // note, I'm not sure why XMLConfiguration.addBlobStore() only rolls-back on UnsuitableStorageException and not
        // on IOException or GeoWebCacheException
        // doThrow(new IOException("fake")).when(listener).handleAddBlobStore(Mockito.any());
        // assertAddBlobStoreFails(store1, IOException.class);
        //
        // doThrow(new GeoWebCacheException("fake")).when(listener).handleAddBlobStore(Mockito.any());
        // assertAddBlobStoreFails(store1, GeoWebCacheException.class);
    }

    private void assertAddBlobStoreFails(FileBlobStoreInfo store, Class<? extends Exception> expectedCause)
            throws ConfigurationException {
        ConfigurationPersistenceException expected;
        expected = assertThrows(ConfigurationPersistenceException.class, () -> config.addBlobStore(store));
        assertThat(expected.getCause(), instanceOf(expectedCause));
        assertEquals(0, config.getBlobStoreCount());
        GeoWebCacheConfiguration configuration = config.loadConfiguration();
        assertTrue("store shouldn't be saved", configuration.getBlobStores().isEmpty());
    }

    /**
     * Verifies the blobstore configuration is rolled back from the persisted configuration if a
     * {@link BlobStoreConfigurationListener#handleModifyBlobStore(BlobStoreInfo)} throws an
     * {@link UnsuitableStorageException}.
     *
     * <p>Note I'm not sure why XMLConfiguration.modifyBobstore() only rolls-back on UnsuitableStorageException and not
     * on IOException or GeoWebCacheException
     */
    @Test
    public void testModifyBlobStoreExceptionFromListener() throws Exception {
        FileBlobStoreInfo original = new FileBlobStoreInfo();
        original.setName("store1");
        original.setDefault(true);
        original.setEnabled(true);
        original.setFileSystemBlockSize(8096);
        original.setBaseDirectory("/tmp/test");

        config.addBlobStore(original);

        BlobStoreConfigurationListener listener;

        listener = mock(BlobStoreConfigurationListener.class);
        doThrow(new UnsuitableStorageException("fake")).when(listener).handleModifyBlobStore(Mockito.any());
        config.addBlobStoreListener(listener);

        assertModifyBlobStoreFails(original, UnsuitableStorageException.class);

        // note, I'm not sure why XMLConfiguration.addBlobStore() only rolls-back on UnsuitableStorageException and not
        // on IOException or GeoWebCacheException
        // doThrow(new IOException("fake")).when(listener).handleModifyBlobStore(Mockito.any());
        // assertModifyBlobStoreFails(original, IOException.class);
        //
        // doThrow(new GeoWebCacheException("fake")).when(listener).handleModifyBlobStore(Mockito.any());
        // assertModifyBlobStoreFails(original, GeoWebCacheException.class);
    }

    private void assertModifyBlobStoreFails(FileBlobStoreInfo original, Class<? extends Exception> expectedCause)
            throws ConfigurationException {

        FileBlobStoreInfo modified = (FileBlobStoreInfo) original.clone();
        modified.setBaseDirectory("/tmp/test2");

        assertEquals(1, config.getBlobStoreCount());

        ConfigurationPersistenceException expected;
        expected = assertThrows(ConfigurationPersistenceException.class, () -> config.modifyBlobStore(modified));
        assertThat(expected.getCause(), instanceOf(expectedCause));
        GeoWebCacheConfiguration reloaded = config.loadConfiguration();
        assertEquals(1, reloaded.getBlobStores().size());

        BlobStoreInfo stored = reloaded.getBlobStores().get(0);
        assertEquals("store shouldn't be saved", original, stored);
    }

    @Test
    public void testSaveCurrentVersion() throws Exception {

        URL source = XMLConfiguration.class.getResource(XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE);
        configFile = new File(configDir, "geowebcache.xml");
        FileUtils.copyURLToFile(source, configFile);

        gridSetBroker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true)));
        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.setGridSetBroker(gridSetBroker);
        config.afterPropertiesSet();
        config.getLayerCount();

        final String previousVersion = config.getVersion();
        assertNotNull(previousVersion);

        // Do a modify without any changes to trigger a save;
        config.modifyLayer(
                config.getLayer(config.getLayerNames().iterator().next()).get());

        final String currVersion = XMLConfiguration.getCurrentSchemaVersion();
        assertNotNull(currVersion);
        assertNotEquals(previousVersion, currVersion);

        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.setGridSetBroker(gridSetBroker);
        config.afterPropertiesSet();
        config.getLayerCount();
        final String savedVersion = config.getVersion();
        assertEquals(currVersion, savedVersion);
    }

    @Test
    public void testWmtsCiteStrictComplianceIsActivated() throws Exception {
        // delete existing GWC configuration file
        assertThat(configFile.delete(), is(true));
        // instantiate a new one based with strict CITE compliance activated
        config.setTemplate("/geowebcache_cite.xml");
        config.setGridSetBroker(gridSetBroker);
        config.deinitialize();
        config.reinitialize();
        config.getLayerCount();
        // CITE strict compliance should be activated for WMTS
        assertThat(config.isWmtsCiteCompliant(), is(true));
    }

    @Test
    public void loadFromReadOnlyDirectory() throws GeoWebCacheException {
        Assume.assumeTrue(
                "Ignore if setWritable(false) does not succeed, may happen on Windows", configDir.setWritable(false));
        assertThat(configFile.exists(), is(true));
        try {
            config = new XMLConfiguration(null, configDir.getAbsolutePath());
            config.setGridSetBroker(gridSetBroker);
            config.afterPropertiesSet();
            assertThat(config.getLayerCount(), is(greaterThan(0)));
        } finally {
            configDir.setWritable(true);
        }
    }

    @Test
    public void loadFromEmptyReadOnlyDirectoryFails() throws GeoWebCacheException, IOException {
        File roEmptyDir = this.temp.newFolder();

        // Try to make it read-only
        roEmptyDir.setWritable(false);

        // Actively probe whether the directory is still writable (for gwc-release Docker on Windows)
        File probe = new File(roEmptyDir, "probe");
        boolean canWrite;
        try {
            canWrite = probe.createNewFile();
            if (canWrite) {
                probe.delete();
            }
        } catch (IOException e) {
            canWrite = false;
        }

        // Skip if we can still write, this may happen on Windows
        Assume.assumeTrue("Skipping: directory is still writable", !canWrite);

        try {
            config = new XMLConfiguration(null, roEmptyDir.getAbsolutePath());
            config.setGridSetBroker(gridSetBroker);

            assertThrows(ConfigurationException.class, () -> config.afterPropertiesSet());
        } finally {
            roEmptyDir.setWritable(true);
        }
    }
}
