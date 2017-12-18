package org.geowebcache.service.wmts;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.Validator;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.config.legends.LegendInfo;
import org.geowebcache.config.legends.LegendInfoBuilder;
import org.geowebcache.config.meta.ServiceContact;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.config.meta.ServiceProvider;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.conveyor.Conveyor.RequestHandler;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.XMLBuilder;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.meta.MetadataURL;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.service.OWSException;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.NullURLMangler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;

import static org.junit.Assert.*;

public class WMTSServiceTest {

    private WMTSService service;

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private GridSetBroker gridsetBroker;

    @Before
    public void setUp() throws Exception {
        sb = mock(StorageBroker.class);
        tld = mock(TileLayerDispatcher.class);
        gridsetBroker = new GridSetBroker(true, true);
    }

    private TileLayer mockTileLayer(String layerName, List<String> gridSetNames,
            List<ParameterFilter> parameterFilters) throws Exception {
        return mockTileLayer(layerName, gridSetNames, parameterFilters, true);
    }

    private TileLayer mockTileLayer(String layerName, List<String> gridSetNames, List<ParameterFilter> parameterFilters, boolean advertised) throws Exception {

        TileLayer tileLayer = mock(TileLayer.class);
        when(tld.getTileLayer(eq(layerName))).thenReturn(tileLayer);
        when(tileLayer.getName()).thenReturn(layerName);
        when(tileLayer.isEnabled()).thenReturn(true);
        when(tileLayer.isAdvertised()).thenReturn(advertised);

        final MimeType mimeType1 = MimeType.createFromFormat("image/png");
        final MimeType mimeType2 = MimeType.createFromFormat("image/jpeg");
        when(tileLayer.getMimeTypes()).thenReturn(Arrays.asList(mimeType1, mimeType2));
        
        final MimeType infoMimeType1 = MimeType.createFromFormat("text/plain");
        final MimeType infoMimeType2 = MimeType.createFromFormat("text/html");
        final MimeType infoMimeType3 = MimeType.createFromFormat("application/vnd.ogc.gml");
        when(tileLayer.getInfoMimeTypes())
                .thenReturn(Arrays.asList(infoMimeType1, infoMimeType2, infoMimeType3));

        Map<String, GridSubset> subsets = new HashMap<String, GridSubset>();
        Map<SRS, List<GridSubset>> bySrs = new HashMap<SRS, List<GridSubset>>();

        GridSetBroker broker = gridsetBroker;

        for (String gsetName : gridSetNames) {
            GridSet gridSet = broker.get(gsetName);
            XMLGridSubset xmlGridSubset = new XMLGridSubset();
            String gridSetName = gridSet.getName();
            xmlGridSubset.setGridSetName(gridSetName);
            GridSubset gridSubSet = xmlGridSubset.getGridSubSet(broker);
            subsets.put(gsetName, gridSubSet);

            List<GridSubset> list = bySrs.get(gridSet.getSrs());
            if (list == null) {
                list = new ArrayList<GridSubset>();
                bySrs.put(gridSet.getSrs(), list);
            }
            list.add(gridSubSet);

            when(tileLayer.getGridSubset(eq(gsetName))).thenReturn(gridSubSet);

        }

        for (SRS srs : bySrs.keySet()) {
            List<GridSubset> list = bySrs.get(srs);
            when(tileLayer.getGridSubsetsForSRS(eq(srs))).thenReturn(list);

        }
        when(tileLayer.getGridSubsets()).thenReturn(subsets.keySet());
        
        when(tileLayer.getParameterFilters()).thenReturn(parameterFilters);

        // sanity check
        for (String gsetName : gridSetNames) {
            assertTrue(tileLayer.getGridSubsets().contains(gsetName));
            assertNotNull(tileLayer.getGridSubset(gsetName));
        }

        return tileLayer;
    }

    @Test
    public void testGetCap() throws Exception {

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        service = new WMTSService(sb, tld,null , mock(RuntimeStats.class));
    
        @SuppressWarnings("unchecked")
        Map<String, String[]> kvp = new CaseInsensitiveMap();
        kvp.put("service", new String[]{"WMTS"});
        kvp.put("version", new String[]{"1.0.0"});
        kvp.put("request", new String[]{"GetCapabilities"});
       
        
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        
        
        {
            List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326");
            
            TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames, Collections.<ParameterFilter>emptyList());
            TileLayer tileLayerUn = mockTileLayer("mockLayerUnadv", gridSetNames, Collections.<ParameterFilter>emptyList(), false);
            when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer, tileLayerUn));

            // add styles
            StringParameterFilter styles = new StringParameterFilter();
            styles.setKey("STYLES");
            styles.setValues(Arrays.asList("style-a", "style-b"));
            when(tileLayer.getParameterFilters()).thenReturn(Collections.singletonList(styles));
            // add legend info for style-a
            TileLayer.LegendInfo legendInfo1 = TileLayer.createLegendInfo();
            legendInfo1.id = "styla-a-legend";
            legendInfo1.width = 250;
            legendInfo1.height = 500;
            legendInfo1.format = "image/jpeg";
            legendInfo1.legendUrl = "https://some-url?some-parameter=value1&another-parameter=value2";
            when(tileLayer.getLegendsInfo()).thenReturn(Collections.singletonMap("style-a", legendInfo1));
            // add legend info for style-b
            LegendInfo legendInfo2 = new LegendInfoBuilder()
                    .withStyleName("styla-b-legend")
                    .withWidth(125)
                    .withHeight(130)
                    .withFormat("image/png")
                    .withCompleteUrl("https://some-url?some-parameter=value3&another-parameter=value4")
                    .withMinScale(5000D)
                    .withMaxScale(10000D)
                    .build();
            when(tileLayer.getLayerLegendsInfo()).thenReturn(Collections.singletonMap("style-b", legendInfo2));

            // add some layer metadata
            MetadataURL metadataURL = new MetadataURL("some-type", "some-format", new URL("http://localhost:8080/some-url"));
            when(tileLayer.getMetadataURLs()).thenReturn(Collections.singletonList(metadataURL));
        }
    
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        
        final String layerName = conv.getLayerId();
        assertNull(layerName);
        
        assertEquals(Conveyor.RequestHandler.SERVICE,conv.reqHandler);
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld,gridsetBroker, conv.servletReq,"http://localhost:8080", "/geowebcache", new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp,mock(RuntimeStats.class));   
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml", resp.getHeader("content-disposition"));                            
    
        // System.out.println(resp.getContentAsString());
        
        String result = resp.getContentAsString();
        
        // Ensure the advertised Layer is contained and the unadvertised not
        assertTrue(result.contains("mockLayer"));
        assertFalse(result.contains("mockLayerUnadv"));
        
        //Validator validator = new Validator(result);
        //validator.useXMLSchema(true);
        //validator.assertIsValid();
        
        Document doc = XMLUnit.buildTestDocument(result);
        Map<String, String> namespaces = new HashMap<>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("ows", "http://www.opengis.net/ows/1.1");        
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
        XpathEngine xpath = XMLUnit.newXpathEngine();
        
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer)", doc));
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer'])", doc));
        assertEquals("2", xpath.evaluate("count(//wmts:Contents/wmts:Layer/wmts:Style/ows:Identifier)", doc));
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer/wmts:Style[ows:Identifier='style-a'])", doc));
        // checking that style-a has the correct legend url
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer/wmts:Style[ows:Identifier='style-a']/wmts:LegendURL" +
                "[@width='250'][@height='500'][@format='image/jpeg'][@xlink:href='https://some-url?some-parameter=value1&another-parameter=value2'])", doc));
        // checking that style-b has the correct legend url
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer/wmts:Style[ows:Identifier='style-b']/wmts:LegendURL" +
                "[@width='125'][@height='130'][@format='image/png'][@minScaleDenominator='5000.0'][@maxScaleDenominator='10000.0']" +
                "[@xlink:href='https://some-url?some-parameter=value3&another-parameter=value4'])", doc));
        // checking that the layer has an associated metadata URL
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer/wmts:MetadataURL[@type='some-type'][wmts:Format='some-format'])", doc));
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer/wmts:MetadataURL[@type='some-type']" +
                "/wmts:OnlineResource[@xlink:href='http://localhost:8080/some-url'])", doc));
        // checking that the layer has an associated tile resource URL, for each supported image
        // format of the layer
        
        assertEquals("1", xpath.evaluate(
                "count(//wmts:Contents/wmts:Layer/wmts:ResourceURL[@resourceType='tile']"
                + "[@format='image/jpeg']"
                + "[@template='http://localhost:8080/geowebcache" + WMTSService.REST_PATH + "/mockLayer/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/jpeg'])", doc));
        assertEquals("1", xpath.evaluate(
                "count(//wmts:Contents/wmts:Layer/wmts:ResourceURL[@resourceType='tile']"
                + "[@format='image/png']"
                + "[@template='http://localhost:8080/geowebcache" + WMTSService.REST_PATH + "/mockLayer/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/png'])", doc));
        // checking that the layer has an associated feature info resources URL, for each supported
        // feature info format of the layer
        assertEquals("1", xpath.evaluate(
                "count(//wmts:Contents/wmts:Layer/wmts:ResourceURL[@resourceType='FeatureInfo']"
                + "[@format='text/plain']"
                + "[@template='http://localhost:8080/geowebcache" + WMTSService.REST_PATH + "/mockLayer/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=text/plain'])", doc));
        assertEquals("1", xpath.evaluate(
                "count(//wmts:Contents/wmts:Layer/wmts:ResourceURL[@resourceType='FeatureInfo']"
                + "[@format='text/html']"
                + "[@template='http://localhost:8080/geowebcache" + WMTSService.REST_PATH + "/mockLayer/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=text/html'])", doc));
        assertEquals("1", xpath.evaluate(
                "count(//wmts:Contents/wmts:Layer/wmts:ResourceURL[@resourceType='FeatureInfo']"
                + "[@format='application/vnd.ogc.gml']"
                + "[@template='http://localhost:8080/geowebcache" + WMTSService.REST_PATH + "/mockLayer/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=application/vnd.ogc.gml'])", doc));        
        // Checking the service metadata URL
        assertEquals("1", xpath.evaluate(
                "count(//wmts:ServiceMetadataURL[@xlink:href='http://localhost:8080/geowebcache" + WMTSService.SERVICE_PATH + "?SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0'])", doc));
        assertEquals("1", xpath.evaluate(
                "count(//wmts:ServiceMetadataURL[@xlink:href='http://localhost:8080/geowebcache" + WMTSService.REST_PATH + "/WMTSCapabilities.xml'])", doc));
    }

    @Test
    public void testGetCapWithExtensions() throws Exception {
        // setup some WMTS extensions
        List<WMTSExtension> extensions = new ArrayList<>();
        extensions.add(new WMTSExtension() {
            @Override
            public String[] getSchemaLocations() {
                return new String[]{"name-space schema-location"};
            }

            @Override
            public void registerNamespaces(XMLBuilder xml) throws IOException {
                xml.attribute("xmlns:custom", "custom");
            }

            @Override
            public void encodedOperationsMetadata(XMLBuilder xml) throws IOException {
                xml.startElement("custom-metadata");
                xml.endElement("custom-metadata");
            }

            @Override
            public List<OperationMetadata> getExtraOperationsMetadata() throws IOException {
                return Arrays.asList(new OperationMetadata("ExtraOperation1"),
                        new OperationMetadata("ExtraOperation2", "custom-url"));
            }

            @Override
            public ServiceInformation getServiceInformation() {
                ServiceInformation serviceInformation = new ServiceInformation();
                serviceInformation.setTitle("custom-service");
                return serviceInformation;
            }

            @Override
            public Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response, StorageBroker storageBroker) throws GeoWebCacheException, OWSException {
                return null;
            }

            @Override
            public boolean handleRequest(Conveyor conveyor) throws OWSException {
                return false;
            }

            @Override
            public void encodeLayer(XMLBuilder xmlBuilder, TileLayer tileLayer) throws IOException {
                xmlBuilder.simpleElement("extra-layer-metadata", "metadatada", true);
            }
        });
        extensions.add(new WMTSExtensionImpl() {
            @Override
            public ServiceInformation getServiceInformation() {
                ServiceInformation serviceInformation = new ServiceInformation();
                ServiceProvider serviceProvider = new ServiceProvider();
                serviceProvider.setProviderName("custom-provider");
                serviceInformation.setServiceProvider(serviceProvider);
                ServiceContact contactInformation = new ServiceContact();
                contactInformation.setPositionName("custom-position");
                serviceProvider.setServiceContact(contactInformation);
                return serviceInformation;
            }
        });
        extensions.add(new WMTSExtensionImpl());
        // mock execution context
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        service = new WMTSService(sb, tld, null, mock(RuntimeStats.class));
        extensions.forEach(service::addExtension);
        @SuppressWarnings("unchecked")
        Map<String, String[]> kvp = new CaseInsensitiveMap();
        kvp.put("service", new String[]{"WMTS"});
        kvp.put("version", new String[]{"1.0.0"});
        kvp.put("request", new String[]{"GetCapabilities"});
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326");
        TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames, Collections.<ParameterFilter>emptyList());
        when(tld.getLayerList()).thenReturn(Collections.singletonList(tileLayer));
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        assertEquals(Conveyor.RequestHandler.SERVICE, conv.reqHandler);
        // perform the get capabilities request
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld, gridsetBroker, conv.servletReq, "http://localhost:8080", "/service/wmts",
                new NullURLMangler(), extensions);
        wmsCap.writeResponse(conv.servletResp, mock(RuntimeStats.class));
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml", resp.getHeader("content-disposition"));
        String result = resp.getContentAsString();
        assertTrue(result.contains("xmlns:custom=\"custom\""));
        assertTrue(result.contains("name-space schema-location"));
        // instantiate the xpath engine
        Document doc = XMLUnit.buildTestDocument(result);
        Map<String, String> namespaces = new HashMap<>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("ows", "http://www.opengis.net/ows/1.1");
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
        XpathEngine xpath = XMLUnit.newXpathEngine();
        // checking that we have the service extra information
        assertEquals("1", xpath.evaluate("count(//wmts:custom-metadata)", doc));
        assertEquals("1", xpath.evaluate("count(//ows:ServiceIdentification[ows:Title='custom-service'])", doc));
        assertEquals("1", xpath.evaluate("count(//ows:ServiceProvider[ows:ProviderName='custom-provider'])", doc));
        assertEquals("1", xpath.evaluate("count(//ows:ServiceProvider/ows:ServiceContact[ows:PositionName='custom-position'])", doc));
        // checking that the extra operations were encoded
        assertEquals("1", xpath.evaluate("count(//ows:OperationsMetadata/ows:Operation[@name='ExtraOperation1'])", doc));
        assertEquals("1", xpath.evaluate("count(//ows:OperationsMetadata/ows:Operation[@name='ExtraOperation1']" +
                "/ows:DCP/ows:HTTP/ows:Get[@xlink:href='http://localhost:8080/service/wmts/service/wmts?'])", doc));
        assertEquals("1", xpath.evaluate("count(//ows:OperationsMetadata/ows:Operation[@name='ExtraOperation2'])", doc));
        assertEquals("1", xpath.evaluate("count(//ows:OperationsMetadata/ows:Operation[@name='ExtraOperation2']" +
                "/ows:DCP/ows:HTTP/ows:Get[@xlink:href='custom-url?'])", doc));
        // checking that layer extra metadata was encoded
        xpath.evaluate("count(//wmts:Contents/wmts:Layer[wmts:extra-layer-metadata='metadatada'])", doc);
    }
    
    @Test
    public void testGetCapOneWGS84BBox() throws Exception {
        
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        service = new WMTSService(sb, tld,null , mock(RuntimeStats.class));
    
        @SuppressWarnings("unchecked")
        Map<String, String[]> kvp = new CaseInsensitiveMap();
        kvp.put("service", new String[]{"WMTS"});
        kvp.put("version", new String[]{"1.0.0"});
        kvp.put("request", new String[]{"GetCapabilities"});
       
    
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        
        
        {
            List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326", "EPSG:900913");
            
            TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames, Collections.<ParameterFilter>emptyList());
            TileLayer tileLayerUn = mockTileLayer("mockLayerUnadv", gridSetNames, Collections.<ParameterFilter>emptyList(), false);
            when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer, tileLayerUn));
            GridSubset wgs84Subset = mock(GridSubset.class);
            when(wgs84Subset.getOriginalExtent()).thenReturn(new BoundingBox(-42d, -24d, 40d, 50d));
            GridSubset googleSubset = mock(GridSubset.class);
            when(googleSubset.getOriginalExtent()).thenReturn(new BoundingBox(1_000_000d, 2_000_000d, 1_000_000d, 2_000_000d));
            when(tileLayer.getGridSubsetForSRS(SRS.getEPSG4326())).thenReturn(wgs84Subset);
            when(tileLayer.getGridSubsetForSRS(SRS.getEPSG900913())).thenReturn(googleSubset);
        }
    
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        
        final String layerName = conv.getLayerId();
        assertNull(layerName);
        
        assertEquals(Conveyor.RequestHandler.SERVICE,conv.reqHandler);
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld,gridsetBroker, conv.servletReq,"http://localhost:8080", "/geowebcache", new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp,mock(RuntimeStats.class));   
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml", resp.getHeader("content-disposition"));                            
    
        // System.out.println(resp.getContentAsString());
        
        String result = resp.getContentAsString();
        
        // Ensure the advertised Layer is contained and the unadvertised not
        assertTrue(result.contains("mockLayer"));
        assertFalse(result.contains("mockLayerUnadv"));
        
        //Validator validator = new Validator(result);
        //validator.useXMLSchema(true);
        //validator.assertIsValid();
        
        Document doc = XMLUnit.buildTestDocument(result);
        Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("ows", "http://www.opengis.net/ows/1.1");
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
        XpathEngine xpath = XMLUnit.newXpathEngine();
        
        assertEquals("1", xpath.evaluate("count(//ows:WGS84BoundingBox)", doc));
    }

    @Test
    public void testGetCapUnboundedStyleFilter() throws Exception {
        
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        service = new WMTSService(sb, tld,null , mock(RuntimeStats.class));
    
        @SuppressWarnings("unchecked")
        Map<String, String[]> kvp = new CaseInsensitiveMap();
        kvp.put("service", new String[]{"WMTS"});
        kvp.put("version", new String[]{"1.0.0"});
        kvp.put("request", new String[]{"GetCapabilities"});
       
    
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        
        
        {
            List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326");
            
            ParameterFilter styleFilter = mock(ParameterFilter.class);
            when(styleFilter.getKey()).thenReturn("STYLES");
            when(styleFilter.getDefaultValue()).thenReturn("Foo");
            when(styleFilter.getLegalValues()).thenReturn(null);
            
            TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames, Collections.singletonList(styleFilter));
            when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        }
    
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        
        final String layerName = conv.getLayerId();
        assertNull(layerName);
        
        assertEquals(Conveyor.RequestHandler.SERVICE,conv.reqHandler);
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld,gridsetBroker, conv.servletReq,"http://localhost:8080", "/geowebcache", new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp,mock(RuntimeStats.class));   
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml", resp.getHeader("content-disposition"));                            
    
        // System.out.println(resp.getContentAsString());
        
        String result = resp.getContentAsString();
        
        //Validator validator = new Validator(result);
        //validator.useXMLSchema(true);
        //validator.assertIsValid();
        
        Document doc = XMLUnit.buildTestDocument(result);
        Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("ows", "http://www.opengis.net/ows/1.1");        
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
        XpathEngine xpath = XMLUnit.newXpathEngine();
        
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer)", doc));
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer'])", doc));
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer/wmts:Style/ows:Identifier)", doc));
        assertEquals("", xpath.evaluate("//wmts:Contents/wmts:Layer/wmts:Style/ows:Identifier", doc));
    }
    
    @Test
    public void testGetCapEmptyStyleFilter() throws Exception {
        
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        service = new WMTSService(sb, tld,null , mock(RuntimeStats.class));
    
        @SuppressWarnings("unchecked")
        Map<String, String[]> kvp = new CaseInsensitiveMap();
        kvp.put("service", new String[]{"WMTS"});
        kvp.put("version", new String[]{"1.0.0"});
        kvp.put("request", new String[]{"GetCapabilities"});
       
    
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        
        
        {
            List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326");
            
            ParameterFilter styleFilter = mock(ParameterFilter.class);
            when(styleFilter.getKey()).thenReturn("STYLES");
            when(styleFilter.getDefaultValue()).thenReturn("Foo");
            when(styleFilter.getLegalValues()).thenReturn(Collections.<String>emptyList());
            
            TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames, Collections.singletonList(styleFilter));
            when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        }
    
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        
        final String layerName = conv.getLayerId();
        assertNull(layerName);
        
        assertEquals(Conveyor.RequestHandler.SERVICE,conv.reqHandler);
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld,gridsetBroker, conv.servletReq,"http://localhost:8080", "/geowebcache", new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp,mock(RuntimeStats.class));   
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml", resp.getHeader("content-disposition"));                            
    
        // System.out.println(resp.getContentAsString());
        
        String result = resp.getContentAsString();
        
        Validator validator = new Validator(result);
        validator.useXMLSchema(true);
        validator.assertIsValid();
        
        Document doc = XMLUnit.buildTestDocument(result);
        Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("ows", "http://www.opengis.net/ows/1.1");        
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
        XpathEngine xpath = XMLUnit.newXpathEngine();
        
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer)", doc));
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer'])", doc));
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer/wmts:Style/ows:Identifier)", doc));
        assertEquals("", xpath.evaluate("//wmts:Contents/wmts:Layer/wmts:Style/ows:Identifier", doc));
    }
    
    @Test
    public void testGetCapMultipleStyles() throws Exception {
        
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        service = new WMTSService(sb, tld,null , mock(RuntimeStats.class));
    
        @SuppressWarnings("unchecked")
        Map<String, String[]> kvp = new CaseInsensitiveMap();
        kvp.put("service", new String[]{"WMTS"});
        kvp.put("version", new String[]{"1.0.0"});
        kvp.put("request", new String[]{"GetCapabilities"});
       
    
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        
        
        {
            List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326");
            
            ParameterFilter styleFilter = mock(ParameterFilter.class);
            when(styleFilter.getKey()).thenReturn("STYLES");
            when(styleFilter.getDefaultValue()).thenReturn("Foo");
            when(styleFilter.getLegalValues()).thenReturn(Arrays.asList("Foo", "Bar", "Baz"));
            
            TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames, Collections.singletonList(styleFilter));
            when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        }
    
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        
        final String layerName = conv.getLayerId();
        assertNull(layerName);
        
        assertEquals(Conveyor.RequestHandler.SERVICE,conv.reqHandler);
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld,gridsetBroker, conv.servletReq,"http://localhost:8080", "/geowebcache", new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp,mock(RuntimeStats.class));   
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml", resp.getHeader("content-disposition"));                            
    
        // System.out.println(resp.getContentAsString());
        
        String result = resp.getContentAsString();
        
        //Validator validator = new Validator(result);
        //validator.useXMLSchema(true);
        //validator.assertIsValid();
        
        Document doc = XMLUnit.buildTestDocument(result);
        Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("ows", "http://www.opengis.net/ows/1.1");        
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
        XpathEngine xpath = XMLUnit.newXpathEngine();
        
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer)", doc));
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer'])", doc));
        // There should be three styles
        assertEquals("3", xpath.evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer']/wmts:Style/ows:Identifier)", doc));
        // Exactly one should be marked default
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer']/wmts:Style[@isDefault='true']/ows:Identifier)", doc));
        // That one should be 'Foo'
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer']/wmts:Style[@isDefault='true']/ows:Identifier[text()='Foo'])", doc));
        // Each of Bar and Baz should also occur
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer']/wmts:Style/ows:Identifier[text()='Bar'])", doc));
        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer']/wmts:Style/ows:Identifier[text()='Baz'])", doc));
    }
    
    /**
     * Generates a layer with "elevation" and "time" dimensions and mime types "image/png" , "image/jpeg" , "text/plain" , "text/html" , "application/vnd.ogc.gml"
     * then checks if in the capabilities documents each <ResourceURL> elements contains both the dimensions components.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetCapWithMultipleDimensions() throws Exception {

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMTSService(sb, tld, null, mock(RuntimeStats.class));

        Map<String, String[]> kvp = new CaseInsensitiveMap();
        kvp.put("service", new String[] { "WMTS" });
        kvp.put("version", new String[] { "1.0.0" });
        kvp.put("request", new String[] { "GetCapabilities" });

        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);

        {
            List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale",
                    "EPSG:4326");

            ParameterFilter styleFilter = mock(ParameterFilter.class);
            when(styleFilter.getKey()).thenReturn("STYLES");
            when(styleFilter.getDefaultValue()).thenReturn("Foo");
            when(styleFilter.getLegalValues()).thenReturn(Arrays.asList("Foo", "Bar", "Baz"));

            ParameterFilter elevationDimension = mock(ParameterFilter.class);
            when(elevationDimension.getKey()).thenReturn("elevation");
            when(elevationDimension.getDefaultValue()).thenReturn("0");
            when(elevationDimension.getLegalValues())
                    .thenReturn(Arrays.asList("0", "200", "400", "600"));

            ParameterFilter timeDimension = mock(ParameterFilter.class);
            when(timeDimension.getKey()).thenReturn("time");
            when(timeDimension.getDefaultValue()).thenReturn("2016-02-23T03:00:00.00");
            when(timeDimension.getLegalValues()).thenReturn(Collections.<String> emptyList());

            TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames,
                    Arrays.asList(styleFilter, elevationDimension, timeDimension));
            when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        }

        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);

        final String layerName = conv.getLayerId();
        assertNull(layerName);

        assertEquals(Conveyor.RequestHandler.SERVICE, conv.reqHandler);
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld, gridsetBroker, conv.servletReq,
                "http://localhost:8080", "/geowebcache", new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp, mock(RuntimeStats.class));
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml",
                resp.getHeader("content-disposition"));

        String result = resp.getContentAsString();

        Document doc = XMLUnit.buildTestDocument(result);
        Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("ows", "http://www.opengis.net/ows/1.1");
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
        XpathEngine xpath = XMLUnit.newXpathEngine();

        assertEquals("1", xpath.evaluate("count(//wmts:Contents/wmts:Layer)", doc));
        assertEquals("1", xpath
                .evaluate("count(//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer'])", doc));
        
        assertEquals("5", xpath.evaluate(
                "count(//wmts:Contents/wmts:Layer/wmts:ResourceURL"
                + "[contains(@template,'&elevation={elevation}&time={time}')])", doc));        
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testGetTileWithStyle() throws Exception {
        
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        service = new WMTSService(sb, tld,null , mock(RuntimeStats.class));
    
        Map<String, String[]> kvp = new CaseInsensitiveMap();
        kvp.put("service", new String[]{ "WMTS"});
        kvp.put("version", new String[]{ "1.0.0"});
        kvp.put("request", new String[]{ "GetTile"});
        kvp.put("layer", new String[]{ "mockLayer"});
        kvp.put("format", new String[]{ "image/png"});
        kvp.put("TileMatrixSet", new String[]{ "GlobalCRS84Pixel"});
        kvp.put("TileMatrix", new String[]{ "GlobalCRS84Pixel:1"});
        kvp.put("TileRow", new String[]{ "0"});
        kvp.put("TileCol", new String[]{ "0"});
        kvp.put("Style", new String[]{ "Bar"}); // Note singular as required by WMTS
    
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        //when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        
        
        {
            List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326");
            
            ParameterFilter styleFilter = mock(ParameterFilter.class);
            when(styleFilter.getKey()).thenReturn("STYLES");
            when(styleFilter.getDefaultValue()).thenReturn("Foo");
            when(styleFilter.getLegalValues()).thenReturn(Arrays.asList("Foo", "Bar", "Baz"));
            
            TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames, Collections.singletonList(styleFilter));
            
            // Style parameter should have been made plural by the time getModifiableParameters is called.
            Map<String, String> map = new HashMap<>();
            map.put("STYLES", "Bar");
            when(tileLayer.getModifiableParameters(
                    (Map)argThat(
                            hasEntry(
                                    equalToIgnoringCase("styles"), 
                                    arrayContaining(equalToIgnoringCase("Bar")))), 
                    (String)any()))
                    .thenReturn(Collections.unmodifiableMap(map));
            when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        }
    
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        
        final String layerName = conv.getLayerId();
        assertEquals("mockLayer", layerName);
        
        assertThat(conv, instanceOf(ConveyorTile.class));
        
        ConveyorTile tile = (ConveyorTile) conv;
        
        Map<String,String> parameters = tile.getParameters();
        assertThat(parameters, hasEntry("STYLES", "Bar")); // Changed to plural, as used by WMS.
    }

    @Test
    public void testDispatchCustomOperations() throws Exception {
        // instantiating all the necessary machinery to perform the request
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        service = new WMTSService(sb, tld, null, mock(RuntimeStats.class));
        @SuppressWarnings("unchecked")
        Map<String, String[]> kvp = new CaseInsensitiveMap();
        kvp.put("service", new String[]{"WMTS"});
        kvp.put("version", new String[]{"1.0.0"});
        kvp.put("request", new String[]{"CustomOperation"});
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        // setup a wmts extension
        service.addExtension(new WMTSExtensionImpl() {

            @Override
            public Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response,
                                        StorageBroker storageBroker) throws GeoWebCacheException, OWSException {
                if ((request.getParameterMap().get("request")[0]).equalsIgnoreCase("CustomOperation")) {
                    Conveyor conveyor = new ConveyorTile(sb, null, req, resp);
                    conveyor.setHint("CustomOperation");
                    return conveyor;
                }
                return null;
            }

            @Override
            public boolean handleRequest(Conveyor conveyor) throws OWSException {
                if (conveyor.getHint().equalsIgnoreCase("CustomOperation")) {
                    try {
                        OutputStream os = conveyor.servletResp.getOutputStream();
                        os.write("CustomOperation Result".getBytes());
                        os.flush();
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                    return true;
                }
                return false;
            }
        });
        // invoke the custom operation
        Conveyor conveyor = service.getConveyor(req, resp);
        assertThat(conveyor, notNullValue());
        service.handleRequest(conveyor);
        assertThat(resp.getContentAsString(), is("CustomOperation Result"));
    }
    
    @Test
    public void testGetFeature() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(false);
        
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        GridSetBroker gsb = mock(GridSetBroker.class);
        
        service = new WMTSService(sb, tld, gsb, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);
        
        GridSubset subset = mock(GridSubset.class);
        GridSet set = mock(GridSet.class);
        
        when(subset.getName()).thenReturn("testGridset");
        when(subset.getGridSet()).thenReturn(set);
        
        when(set.getTileHeight()).thenReturn(256);
        when(set.getTileWidth()).thenReturn(256);
        
        String layerName = "mockLayer";
        TileLayer tileLayer = mock(TileLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);
        when(tld.getLayerList()).thenReturn(Collections.singleton(tileLayer));
        when(tileLayer.getGridSubset("testGridset")).thenReturn(subset);
        when(tileLayer.getInfoMimeTypes()).thenReturn(Collections.singletonList(XMLMime.gml));
        //doThrow(new SecurityException()).when(secDisp).checkSecurity(Mockito.any());
        
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        
        req.addParameter("service", "WMTS");
        req.addParameter("version", "1.0.0");
        req.addParameter("request", "GetFeatureInfo");
        req.addParameter("layer", layerName);
        req.addParameter("format", "image/png");
        req.addParameter("tilematrixset", "testGridset");
        req.addParameter("tilematrix", "testGridset:2");
        req.addParameter("tilerow", "3");
        req.addParameter("tilecol", "4");
        req.addParameter("infoformat", XMLMime.gml.getMimeType());
        req.addParameter("i", "20");
        req.addParameter("j", "50");
        req.setRequestURI("/geowebcache/service/wmts?service=WMTS&version=1.0.0&request=GetFeatureInfo"
                + "&layer="+layerName+"&format=image/png&tilematrixset=testGridset"
                + "&tilematrix=testGridset:2&tilerow=3&tilecol=4&infoformat="
                + XMLMime.gml.getMimeType());
        
        when(subset.getNumTilesHigh(2)).thenReturn(7L);
        when(subset.getGridIndex("testGridset:2")).thenReturn(2L);
        when(subset.getCoverage(2)).thenReturn(new long[] {1,1,8,8});
        
        Conveyor conv = service.getConveyor(req, resp);
        
        assertThat(conv, hasProperty("gridSetId", is("testGridset")));
        
        when(tileLayer.getFeatureInfo(any(ConveyorTile.class), any(BoundingBox.class), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(new ByteArrayResource("TEST FEATURE INFO".getBytes()));
        
        assertThat(conv, hasProperty("hint", equalTo("GetFeatureInfo".toLowerCase())));
        assertThat(conv, hasProperty("requestHandler", is(RequestHandler.SERVICE)));
        
        service.handleRequest(conv);
        //fail("Expected SecurityException");
        
        assertThat(resp.getContentAsString(), equalTo("TEST FEATURE INFO"));
    }
    
    @Test
    public void testGetFeatureSecure() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);
        
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        GridSetBroker gsb = mock(GridSetBroker.class);
        
        service = new WMTSService(sb, tld, gsb, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);
        
        GridSubset subset = mock(GridSubset.class);
        GridSet set = mock(GridSet.class);
        
        when(subset.getName()).thenReturn("testGridset");
        when(subset.getGridSet()).thenReturn(set);
        
        when(set.getTileHeight()).thenReturn(256);
        when(set.getTileWidth()).thenReturn(256);
        
        String layerName = "mockLayer";
        TileLayer tileLayer = mock(TileLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);
        when(tld.getLayerList()).thenReturn(Collections.singleton(tileLayer));
        when(tileLayer.getGridSubset("testGridset")).thenReturn(subset);
        when(tileLayer.getInfoMimeTypes()).thenReturn(Collections.singletonList(XMLMime.gml));
        doThrow(new SecurityException()).when(secDisp).checkSecurity(Mockito.any());
        
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        
        req.addParameter("service", "WMTS");
        req.addParameter("version", "1.0.0");
        req.addParameter("request", "GetFeatureInfo");
        req.addParameter("layer", layerName);
        req.addParameter("format", "image/png");
        req.addParameter("tilematrixset", "testGridset");
        req.addParameter("tilematrix", "testGridset:2");
        req.addParameter("tilerow", "3");
        req.addParameter("tilecol", "4");
        req.addParameter("infoformat", XMLMime.gml.getMimeType());
        req.addParameter("i", "20");
        req.addParameter("j", "50");
        req.setRequestURI("/geowebcache/service/wmts?service=WMTS&version=1.0.0&request=GetFeatureInfo"
                + "&layer="+layerName+"&format=image/png&tilematrixset=testGridset"
                + "&tilematrix=testGridset:2&tilerow=3&tilecol=4&infoformat="
                + XMLMime.gml.getMimeType());
        
        when(subset.getNumTilesHigh(2)).thenReturn(7L);
        when(subset.getGridIndex("testGridset:2")).thenReturn(2L);
        when(subset.getCoverage(2)).thenReturn(new long[] {1,1,8,8});
        
        Conveyor conv = service.getConveyor(req, resp);
        
        assertThat(conv, hasProperty("gridSetId", is("testGridset")));
        
        when(tileLayer.getFeatureInfo(any(ConveyorTile.class), any(BoundingBox.class), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(new ByteArrayResource("TEST FEATURE INFO".getBytes()));
        
        assertThat(conv, hasProperty("hint", equalTo("GetFeatureInfo".toLowerCase())));
        assertThat(conv, hasProperty("requestHandler", is(RequestHandler.SERVICE)));
        
        try {
            service.handleRequest(conv);
            fail("Expected SecurityException");
        } catch (SecurityException ex) { 
            assertThat(resp.getContentAsString(), not(containsString("TEST FEATURE INFO")));
        }
    }

    
}
