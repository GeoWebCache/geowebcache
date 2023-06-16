package org.geowebcache.service.wms;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.Conveyor.RequestHandler;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.layer.ProxyLayer;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.NullURLMangler;
import org.geowebcache.util.PropertyRule;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class WMSServiceTest {

    @Rule
    public PropertyRule whitelistProperty =
            PropertyRule.system(WMSService.GEOWEBCACHE_WMS_PROXY_REQUEST_WHITELIST);

    private WMSService service;

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private GridSetBroker gridsetBroker;

    @Before
    public void setUp() throws Exception {
        sb = mock(StorageBroker.class);
        tld = mock(TileLayerDispatcher.class);
        gridsetBroker =
                new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true)));
    }

    @After
    public void tearDown() throws Exception {}

    /**
     * Layer may be configured with mutliple GridSets for the same CRS, and should chose the best
     * fit for the request
     */
    @Test
    public void testGetConveyorMultipleCrsMatchingGridSubsets() throws Exception {

        testMultipleCrsMatchingGridSubsets("EPSG:4326", "EPSG:4326", new long[] {1, 1, 1});
        testMultipleCrsMatchingGridSubsets("EPSG:4326", "EPSG:4326", new long[] {10, 10, 10});

        testMultipleCrsMatchingGridSubsets("EPSG:4326", "GlobalCRS84Scale", new long[] {1, 1, 1});
        testMultipleCrsMatchingGridSubsets(
                "EPSG:4326", "GlobalCRS84Scale", new long[] {10, 10, 10});

        testMultipleCrsMatchingGridSubsets("EPSG:4326", "GlobalCRS84Scale", new long[] {1, 1, 1});
        testMultipleCrsMatchingGridSubsets(
                "EPSG:4326", "GlobalCRS84Scale", new long[] {10, 10, 10});
    }

    protected void testMultipleCrsMatchingGridSubsets(
            final String srs, final String expectedGridset, long[] tileIndex) throws Exception {

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);

        Map<String, String[]> kvp = new CaseInsensitiveMap<>();
        kvp.put("format", new String[] {"image/png"});

        kvp.put("srs", new String[] {"EPSG:4326"});
        kvp.put("width", new String[] {"256"});
        kvp.put("height", new String[] {"256"});
        kvp.put("layers", new String[] {"mockLayer"});
        kvp.put("tiled", new String[] {"true"});
        kvp.put("request", new String[] {"GetMap"});

        List<String> gridSetNames =
                Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale", "EPSG:4326");
        TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames);

        // make the request match a tile in the expected gridset
        BoundingBox bounds;
        bounds = tileLayer.getGridSubset(expectedGridset).boundsFromIndex(tileIndex);
        kvp.put("bbox", new String[] {bounds.toString()});

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);

        ConveyorTile tileRequest = service.getConveyor(req, resp);
        assertNotNull(tileRequest);

        assertEquals(expectedGridset, tileRequest.getGridSetId());
        assertEquals("image/png", tileRequest.getMimeType().getMimeType());
        assertArrayEquals(
                "Expected "
                        + Arrays.toString(tileIndex)
                        + " got "
                        + Arrays.toString(tileRequest.getTileIndex()),
                tileIndex,
                tileRequest.getTileIndex());
    }

    private TileLayer mockTileLayer(String layerName, List<String> gridSetNames) throws Exception {

        TileLayer tileLayer = mock(TileLayer.class);
        when(tld.getTileLayer(eq(layerName))).thenReturn(tileLayer);
        when(tileLayer.getName()).thenReturn(layerName);
        when(tileLayer.isEnabled()).thenReturn(true);
        when(tileLayer.isAdvertised()).thenReturn(true);

        final MimeType mimeType1 = MimeType.createFromFormat("image/png");
        final MimeType mimeType2 = MimeType.createFromFormat("image/jpeg");
        when(tileLayer.getMimeTypes()).thenReturn(Arrays.asList(mimeType1, mimeType2));

        Map<String, GridSubset> subsets = new HashMap<>();
        Map<SRS, List<GridSubset>> bySrs = new HashMap<>();

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
                list = new ArrayList<>();
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

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);

        Map<String, String[]> kvp = new CaseInsensitiveMap<>();
        kvp.put("service", new String[] {"WMS"});
        kvp.put("version", new String[] {"1.1.1"});
        kvp.put("request", new String[] {"GetCapabilities"});

        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);

        List<String> gridSetNames =
                Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale", "EPSG:4326");
        TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames);
        when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        when(tld.getLayerListFiltered()).thenReturn(Arrays.asList(tileLayer));

        ConveyorTile conv = service.getConveyor(req, resp);
        assertNotNull(conv);

        final String layerName = conv.getLayerId();
        assertNull(layerName);

        assertEquals(Conveyor.RequestHandler.SERVICE, conv.reqHandler);
        WMSGetCapabilities wmsCap =
                new WMSGetCapabilities(
                        tld,
                        conv.servletReq,
                        "http://localhost:8080",
                        "/service/wms",
                        new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp);
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals(
                "inline;filename=wms-getcapabilities.xml", resp.getHeader("content-disposition"));
    }

    @Test
    public void testGetCapEncoding() throws Exception {

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);

        Map<String, String[]> kvp = new CaseInsensitiveMap<>();
        kvp.put("service", new String[] {"WMS"});
        kvp.put("version", new String[] {"1.1.1"});
        kvp.put("request", new String[] {"GetCapabilities"});

        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);

        List<String> gridSetNames =
                Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale", "EPSG:4326");
        TileLayer tileLayer = mockTileLayer("möcklāyer😎", gridSetNames);
        when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        when(tld.getLayerListFiltered()).thenReturn(Arrays.asList(tileLayer));

        ConveyorTile conv = service.getConveyor(req, resp);
        assertNotNull(conv);

        assertEquals(Conveyor.RequestHandler.SERVICE, conv.reqHandler);
        WMSGetCapabilities wmsCap =
                new WMSGetCapabilities(
                        tld,
                        conv.servletReq,
                        "http://localhost:8080",
                        "/service/wms",
                        new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp);

        String capAsString = new String(resp.getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(capAsString, Matchers.containsString("möcklāyer😎"));
    }

    @Test
    public void testGetConveyorWithParameters() throws Exception {

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);

        String layerName = "mockLayer";
        String timeValue = "00:00";

        Map<String, String[]> kvp = new CaseInsensitiveMap<>();
        kvp.put("service", new String[] {"WMS"});
        kvp.put("version", new String[] {"1.1.1"});
        kvp.put("request", new String[] {"GetFeatureInfo"});
        kvp.put("layers", new String[] {layerName});
        kvp.put("time", new String[] {timeValue});

        List<String> mimeFormats = new ArrayList<>();
        mimeFormats.add("image/png");
        List<ParameterFilter> parameterFilters = new ArrayList<>();
        RegexParameterFilter filter = new RegexParameterFilter();
        filter.setKey("time");
        filter.setRegex("\\d{2}:\\d{2}");
        parameterFilters.add(filter);
        TileLayer tileLayer =
                new WMSLayer(
                        layerName,
                        null,
                        null,
                        layerName,
                        mimeFormats,
                        null,
                        parameterFilters,
                        null,
                        null,
                        true,
                        null);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);

        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);

        ConveyorTile conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        assertEquals(Conveyor.RequestHandler.SERVICE, conv.reqHandler);
        assertNotNull(conv.getLayerId());
        assertEquals(layerName, conv.getLayerId());
        assertFalse(conv.getFilteringParameters().isEmpty());
        assertEquals(timeValue, conv.getFilteringParameters().get("TIME"));
    }

    @Test
    public void testProxyRequest() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(false);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);

        testProxyRequestAllowed(secDisp, layerName, tileLayer, "TROZ");
    }

    @Test
    public void testProxyDefaultWhitelistLimitedWhenSecure() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);

        testProxyRequestPrevented(secDisp, layerName, tileLayer, "TROZ");
    }

    @Test
    public void testProxyRequestSecuredDefaultAllowGetLegendGraphic() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);

        testProxyRequestAllowed(secDisp, layerName, tileLayer, "GetLegendGraphic");
    }

    @Test
    public void testProxyRequestDefaultWhitelistRestrictedByFilter() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        doThrow(new SecurityException()).when(secDisp).checkSecurity(Mockito.any());

        testProxyRequestPrevented(secDisp, layerName, tileLayer, "GetLegendGraphic");
    }

    @Test
    public void testProxyRequestWhitelistWhenNoSecurityFilters() throws Exception {
        whitelistProperty.setValue("troz");
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(false);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);
        String layerName = "mockLayer";

        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);

        testProxyRequestAllowed(secDisp, layerName, tileLayer, "TROZ");
        testProxyRequestPrevented(secDisp, layerName, tileLayer, "GetLegendGraphic");
    }

    @Test
    public void testProxyRequestWhitelistWithSecurityFilters() throws Exception {
        whitelistProperty.setValue("troz");
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);

        testProxyRequestAllowed(secDisp, layerName, tileLayer, "TROZ");
        testProxyRequestPrevented(secDisp, layerName, tileLayer, "GetLegendGraphic");
    }

    @Test
    public void testProxyRequestWhitelistWithSecurityFiltersAppliesFilters() throws Exception {
        whitelistProperty.setValue("troz");
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);

        doThrow(new SecurityException()).when(secDisp).checkSecurity(Mockito.any());

        testProxyRequestPrevented(secDisp, layerName, tileLayer, "TROZ");
    }

    // We might eventually do some security on GetCapabilities, but for now we want to make sure
    // nothing breaks.
    @Test
    public void testGetCapabilitiesNotAffectedBySecurityFilter() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);
        when(tld.getLayerList()).thenReturn(Collections.singleton(tileLayer));
        when(tld.getLayerListFiltered()).thenReturn(Collections.singleton(tileLayer));

        doThrow(new SecurityException()).when(secDisp).checkSecurity(Mockito.any());

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.addParameter("service", new String[] {"WMS"});
        req.addParameter("version", new String[] {"1.1.1"});
        req.addParameter("request", new String[] {"GetCapabilities"});
        req.setRequestURI(
                "/geowebcache/service/wms?service=WMS&version=1.1.1&request=" + "GetCapabilities");

        ConveyorTile conv = service.getConveyor(req, resp);

        assertThat(conv, hasProperty("hint", equalTo("GetCapabilities".toLowerCase())));

        service.handleRequest(conv);
    }

    @Test
    public void testGetCapabilitiesNotAffectedByProxyWhitelist() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(false);
        whitelistProperty.setValue("troz");

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);
        when(tld.getLayerList()).thenReturn(Collections.singleton(tileLayer));
        when(tld.getLayerListFiltered()).thenReturn(Collections.singleton(tileLayer));

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.addParameter("service", new String[] {"WMS"});
        req.addParameter("version", new String[] {"1.1.1"});
        req.addParameter("request", new String[] {"GetCapabilities"});
        req.setRequestURI(
                "/geowebcache/service/wms?service=WMS&version=1.1.1&request=" + "GetCapabilities");

        ConveyorTile conv = service.getConveyor(req, resp);

        assertThat(conv, hasProperty("hint", equalTo("GetCapabilities".toLowerCase())));

        service.handleRequest(conv);
    }

    @Test
    public void testTileFuseNotAffectedByProxyWhitelist() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);
        whitelistProperty.setValue("troz");

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        WMSTileFuser fuser = mock(WMSTileFuser.class);

        service =
                new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd) {

                    @Override
                    protected WMSTileFuser getFuser(HttpServletRequest servletReq)
                            throws GeoWebCacheException {
                        return fuser;
                    }
                };
        service.setSecurityDispatcher(secDisp);
        service.setFullWMS("true");

        GridSubset subset = mock(GridSubset.class);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);
        when(tld.getLayerList()).thenReturn(Collections.singleton(tileLayer));
        when(tld.getLayerListFiltered()).thenReturn(Collections.singleton(tileLayer));

        when(tileLayer.getGridSubsetsForSRS(SRS.getEPSG4326()))
                .thenReturn(Collections.singletonList(subset));

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.addParameter("service", "WMS");
        req.addParameter("version", "1.1.1");
        req.addParameter("request", "GetMap");
        req.addParameter("layers", layerName);
        req.addParameter("format", "image/png");
        req.addParameter("srs", SRS.getEPSG4326().toString());
        req.addParameter("bbox", "0,0,40,60");
        req.addParameter("width", "40");
        req.addParameter("height", "60");
        req.setRequestURI(
                "/geowebcache/service/wms?service=WMS&version=1.1.1&request=GetMap&layers="
                        + layerName
                        + "&format=image/png&srs="
                        + SRS.getEPSG4326().toString()
                        + "&bbox=0,0,40,60&width=40&height=60");

        ConveyorTile conv = service.getConveyor(req, resp);

        assertThat(conv, hasProperty("hint", equalTo("GetMap".toLowerCase())));
        assertThat(conv, hasProperty("requestHandler", equalTo(RequestHandler.SERVICE)));

        service.handleRequest(conv);

        verify(fuser).writeResponse(Mockito.same(resp), Mockito.any());
    }

    @Test
    public void testTileFuseSecuredByFilters() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);
        whitelistProperty.setValue("troz");

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        WMSTileFuser fuser = mock(WMSTileFuser.class);

        service =
                new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd) {

                    @Override
                    protected WMSTileFuser getFuser(HttpServletRequest servletReq)
                            throws GeoWebCacheException {
                        return fuser;
                    }
                };
        service.setSecurityDispatcher(secDisp);
        service.setFullWMS("true");

        GridSubset subset = mock(GridSubset.class);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);
        when(tld.getLayerList()).thenReturn(Collections.singleton(tileLayer));
        when(tld.getLayerListFiltered()).thenReturn(Collections.singleton(tileLayer));

        when(tileLayer.getGridSubsetsForSRS(SRS.getEPSG4326()))
                .thenReturn(Collections.singletonList(subset));

        doThrow(new SecurityException()).when(secDisp).checkSecurity(Mockito.any());

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.addParameter("service", "WMS");
        req.addParameter("version", "1.1.1");
        req.addParameter("request", "GetMap");
        req.addParameter("layers", layerName);
        req.addParameter("format", "image/png");
        req.addParameter("srs", SRS.getEPSG4326().toString());
        req.addParameter("bbox", "0,0,40,60");
        req.addParameter("width", "40");
        req.addParameter("height", "60");
        req.setRequestURI(
                "/geowebcache/service/wms?service=WMS&version=1.1.1&request=GetMap&layers="
                        + layerName
                        + "&format=image/png&srs="
                        + SRS.getEPSG4326().toString()
                        + "&bbox=0,0,40,60&width=40&height=60");

        ConveyorTile conv = service.getConveyor(req, resp);

        assertThat(conv, hasProperty("hint", equalTo("GetMap".toLowerCase())));
        assertThat(conv, hasProperty("requestHandler", equalTo(RequestHandler.SERVICE)));

        try {
            service.handleRequest(conv);
            fail("Expected SecurityException");
        } catch (SecurityException ex) {
            verify(fuser, never()).writeResponse(Mockito.same(resp), Mockito.any());
        }
    }

    @Test
    public void testGetFeature() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(false);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        GridSubset subset = mock(GridSubset.class);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);
        when(tld.getLayerList()).thenReturn(Collections.singleton(tileLayer));
        when(tld.getLayerListFiltered()).thenReturn(Collections.singleton(tileLayer));

        when(tileLayer.getGridSubsetsForSRS(SRS.getEPSG4326()))
                .thenReturn(Collections.singletonList(subset));
        when(tileLayer.getInfoMimeTypes()).thenReturn(Collections.singletonList(XMLMime.gml));
        // doThrow(new SecurityException()).when(secDisp).checkSecurity(Mockito.any());

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.addParameter("service", "WMS");
        req.addParameter("version", "1.1.1");
        req.addParameter("request", "GetFeatureInfo");
        req.addParameter("layers", layerName);
        req.addParameter("format", "image/png");
        req.addParameter("srs", SRS.getEPSG4326().toString());
        req.addParameter("bbox", "0,0,40,60");
        req.addParameter("width", "40");
        req.addParameter("height", "60");
        req.addParameter("x", "2");
        req.addParameter("y", "3");
        req.addParameter("info_format", XMLMime.gml.getMimeType());
        req.setRequestURI(
                "/geowebcache/service/wms?service=WMS&version=1.1.1&request=GetFeatureInfo&layers="
                        + layerName
                        + "&format=image/png&srs="
                        + SRS.getEPSG4326().toString()
                        + "&bbox=0,0,40,60&width=40&height=60&x=3&y=3");

        ConveyorTile conv = service.getConveyor(req, resp);

        when(tileLayer.getFeatureInfo(
                        any(ConveyorTile.class),
                        any(BoundingBox.class),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt()))
                .thenReturn(new ByteArrayResource("TEST FEATURE INFO".getBytes()));

        assertThat(conv, hasProperty("hint", equalTo("GetFeatureInfo".toLowerCase())));
        assertThat(conv, hasProperty("requestHandler", equalTo(RequestHandler.SERVICE)));

        service.handleRequest(conv);
        // fail("Expected SecurityException");

        assertThat(resp.getContentAsString(), equalTo("TEST FEATURE INFO"));
    }

    @Test
    public void testGetFeatureSecure() throws Exception {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(true);

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new WMSService(sb, tld, mock(RuntimeStats.class), new NullURLMangler(), gwcd);
        service.setSecurityDispatcher(secDisp);

        GridSubset subset = mock(GridSubset.class);

        String layerName = "mockLayer";
        TestLayer tileLayer = mock(TestLayer.class);
        when(tld.getTileLayer(layerName)).thenReturn(tileLayer);
        when(tld.getLayerList()).thenReturn(Collections.singleton(tileLayer));
        when(tld.getLayerListFiltered()).thenReturn(Collections.singleton(tileLayer));

        when(tileLayer.getGridSubsetsForSRS(SRS.getEPSG4326()))
                .thenReturn(Collections.singletonList(subset));
        when(tileLayer.getInfoMimeTypes()).thenReturn(Collections.singletonList(XMLMime.gml));
        doThrow(new SecurityException()).when(secDisp).checkSecurity(Mockito.any());

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.addParameter("service", "WMS");
        req.addParameter("version", "1.1.1");
        req.addParameter("request", "GetFeatureInfo");
        req.addParameter("layers", layerName);
        req.addParameter("format", "image/png");
        req.addParameter("srs", SRS.getEPSG4326().toString());
        req.addParameter("bbox", "0,0,40,60");
        req.addParameter("width", "40");
        req.addParameter("height", "60");
        req.addParameter("x", "2");
        req.addParameter("y", "3");
        req.addParameter("info_format", XMLMime.gml.getMimeType());
        req.setRequestURI(
                "/geowebcache/service/wms?service=WMS&version=1.1.1&request=GetFeatureInfo&layers="
                        + layerName
                        + "&format=image/png&srs="
                        + SRS.getEPSG4326().toString()
                        + "&bbox=0,0,40,60&width=40&height=60&x=3&y=3");

        ConveyorTile conv = service.getConveyor(req, resp);

        when(tileLayer.getFeatureInfo(
                        any(ConveyorTile.class),
                        any(BoundingBox.class),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt()))
                .thenReturn(new ByteArrayResource("TEST FEATURE INFO".getBytes()));

        assertThat(conv, hasProperty("hint", equalTo("GetFeatureInfo".toLowerCase())));
        assertThat(conv, hasProperty("requestHandler", equalTo(RequestHandler.SERVICE)));

        try {
            service.handleRequest(conv);
            fail("Expected SecurityException");
        } catch (SecurityException ex) {
            assertThat(resp.getContentAsString(), not(containsString("TEST FEATURE INFO")));
        }
    }

    protected void testProxyRequestPrevented(
            SecurityDispatcher mockSecDisp,
            String layerName,
            TestLayer mockTileLayer,
            String requestName)
            throws GeoWebCacheException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.addParameter("service", new String[] {"WMS"});
        req.addParameter("version", new String[] {"1.1.1"});
        req.addParameter("request", new String[] {requestName});
        req.addParameter("layers", new String[] {layerName});
        req.setRequestURI(
                "/geowebcache/service/wms?service=WMS&version=1.1.1&request="
                        + requestName
                        + "&layers="
                        + layerName);

        ConveyorTile conv = service.getConveyor(req, resp);

        assertThat(conv, hasProperty("hint", equalTo(requestName.toLowerCase())));

        try {
            service.handleRequest(conv);
            fail("Expected SecurityException");
        } catch (SecurityException ex) {
            verify(mockTileLayer, never()).proxyRequest(conv);
        }
    }

    protected void testProxyRequestAllowed(
            SecurityDispatcher mockSecDisp,
            String layerName,
            TestLayer mockTileLayer,
            String requestName)
            throws GeoWebCacheException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.addParameter("service", new String[] {"WMS"});
        req.addParameter("version", new String[] {"1.1.1"});
        req.addParameter("request", new String[] {requestName});
        req.addParameter("layers", new String[] {layerName});
        req.setRequestURI(
                "/geowebcache/service/wms?service=WMS&version=1.1.1&request="
                        + requestName
                        + "&layers="
                        + layerName);

        ConveyorTile conv = service.getConveyor(req, resp);

        assertThat(conv, hasProperty("hint", equalTo(requestName.toLowerCase())));

        service.handleRequest(conv);

        verify(mockTileLayer).proxyRequest(conv);
    }

    /** Dummy TileLayer/ProxyLayer for use in mocks */
    protected abstract static class TestLayer extends TileLayer implements ProxyLayer {}
}
