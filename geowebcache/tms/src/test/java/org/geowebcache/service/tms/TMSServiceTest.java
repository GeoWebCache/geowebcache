package org.geowebcache.service.tms;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeType;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.URLMangler;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;

import junit.framework.TestCase;

public class TMSServiceTest extends TestCase {

    private TMSService service;

    private StorageBroker sb;

    private TileLayerDispatcher tld;
    private TileLayerDispatcher customTld;

    private GridSetBroker gridsetBroker;

    private URLMangler httpsUrlMangler;

    private TMSDocumentFactory customFactory;

    protected void setUp() throws Exception {
        sb = mock(StorageBroker.class);
        tld = mock(TileLayerDispatcher.class);
        customTld = mock(TileLayerDispatcher.class);
        gridsetBroker = new GridSetBroker(true, true);
        httpsUrlMangler = new URLMangler() {

            final Pattern PATTERN = Pattern.compile("http");

            @Override
            public String buildURL(String baseURL, String contextPath, String path) {
                String url = StringUtils.strip(baseURL, "/") + "/" + StringUtils.strip(contextPath, "/") + "/"
                        + StringUtils.stripStart(path, "/");
                    url = url.startsWith("https") ? url : PATTERN.matcher(url).replaceFirst("https");

                return url;
            }
        };
        customFactory = new TMSCustomFactoryTest(customTld, gridsetBroker, httpsUrlMangler, TMSCustomFactoryTest.getCatalogInstance());
    }

    private static TileLayer mockTileLayer(TileLayerDispatcher tld, GridSetBroker gridsetBroker, String layerName, List<String> gridSetNames,
            List<ParameterFilter> parameterFilters) throws Exception {
        return mockTileLayer(tld, gridsetBroker, layerName, gridSetNames, parameterFilters, true);
    }

    private static class TMSCustomFactoryTest extends TMSDocumentFactory {

        // Custom layer implementation simulating an external source of information to be used 
        // to populate the tile map metadata document
        static class CustomLayerImplementation {
            private String name;
            private String title;
            private boolean isAuthorized;
            private List<String> formats;
            public CustomLayerImplementation(String name, String title, boolean isAuthorized, List<String> formats) {
                this.name = name;
                this.title = title;
                this.isAuthorized = isAuthorized;
                this.formats = formats;
            }
        }

        private final static List<CustomLayerImplementation> CATALOG_INSTANCE;

        public static List<CustomLayerImplementation> getCatalogInstance() {
            return CATALOG_INSTANCE;
        }

        static {
            CATALOG_INSTANCE = new ArrayList<CustomLayerImplementation>(2);
            CATALOG_INSTANCE.add(new CustomLayerImplementation("customLayer1","Custom Layer1", false, null));
            CATALOG_INSTANCE.add(new CustomLayerImplementation("customLayer2","Custom Layer2", true, Arrays.asList("jpeg-png")));
        }

        private List<CustomLayerImplementation> customCatalogLayers;

        protected TMSCustomFactoryTest(TileLayerDispatcher tld, GridSetBroker gsb, URLMangler urlMangler,
                List<CustomLayerImplementation> customCatalogLayers) throws Exception {
            super(tld, gsb, urlMangler, "tilemapservice", StandardCharsets.UTF_8);
            List<String> gridSetNames = Arrays.asList("EPSG:4326");
            TileLayer tileLayer = mockTileLayer(tld, gsb, "customLayer2", gridSetNames, Collections.<ParameterFilter>emptyList());
            when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
            this.customCatalogLayers = customCatalogLayers;

        }

        @Override
        protected String getTileMapServiceDoc(String baseUrl, String contextPath) {
            StringBuilder str = new StringBuilder();
            str.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
            str.append("<TileMapService version=\"1.0.0\" services=\"" +
                urlMangler.buildURL(baseUrl, contextPath, "") + "\">\n");
            str.append("  <Title>Custom Tile Map Service</Title>\n");
            str.append("  <Abstract>A Custom Tile Map Service served by GeoWebCache</Abstract>\n");
            str.append("  <TileMaps>\n");

            // Custom tileMapService document being populated on top of the
            // custom external catalog source.
            for (CustomLayerImplementation layer: customCatalogLayers) {
               if (layer.isAuthorized) {
                    for (String format : layer.formats) {
                        tileMapsForLayer(str, layer, format, baseUrl, contextPath);
                    }
                }
            }
            str.append("  </TileMaps>\n");
            str.append("</TileMapService>\n");
            return str.toString();
        }

        protected void tileMapsForLayer(StringBuilder str, CustomLayerImplementation layer, String format,
            String baseUrl, String contextPath) {
            str.append("    <TileMap\n");
            str.append("      title=\"").append(layer.title).append("\"\n");
            str.append("      srs=\"").append("4326").append("\"\n");
            str.append("      profile=\"global-geodetic");
            str.append("\"\n");
            str.append("      href=\"");

            String tileMapName = layer.name + "@EPSG:4326" + "@" + format;
            String url= urlMangler.buildURL(baseUrl, contextPath, TMSDocumentFactory.SERVICE_PATH + "/" + tileMapName);
            str.append(url).append("\" />\n");

        }
    }

    private static TileLayer mockTileLayer(TileLayerDispatcher tld, GridSetBroker gridsetBroker, String layerName, List<String> gridSetNames, List<ParameterFilter> parameterFilters, boolean advertised) throws Exception {

        TileLayer tileLayer = mock(TileLayer.class);
        when(tld.getTileLayer(eq(layerName))).thenReturn(tileLayer);
        when(tileLayer.getName()).thenReturn(layerName);
        when(tileLayer.isEnabled()).thenReturn(true);
        when(tileLayer.isAdvertised()).thenReturn(advertised);

        final MimeType mimeType1 = MimeType.createFromFormat("image/png");
        final MimeType mimeType2 = MimeType.createFromFormat("image/jpeg");
        when(tileLayer.getMimeTypes()).thenReturn(Arrays.asList(mimeType1, mimeType2));

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

    public void testTileMapServiceDocument() throws Exception {

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);

        service = new TMSService(sb, tld, gridsetBroker , mock(RuntimeStats.class));

        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getPathInfo()).thenReturn("/service/tms/1.0.0");
        when(req.getRequestURI()).thenReturn("/mycontext/service/tms/1.0.0");
        when(req.getScheme()).thenReturn("http");
        when(req.getServerName()).thenReturn("localhost");
        when(req.getServerPort()).thenReturn(8080);
        when(req.getContextPath()).thenReturn("/mycontext");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/mycontext/service/tms/1.0.0"));
        List<String> gridSetNames = Arrays.asList("EPSG:4326");
        TileLayer tileLayer = mockTileLayer(tld, gridsetBroker, "mockLayer", gridSetNames, Collections.<ParameterFilter>emptyList());
        when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));

        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);

        final String layerName = conv.getLayerId();
        assertNull(layerName);

        assertEquals(Conveyor.RequestHandler.SERVICE,conv.reqHandler);
        service.handleRequest(conv);

        String result = resp.getContentAsString();

        // Ensure the advertised Layer is contained
        assertTrue(result.contains("mockLayer"));

        Document doc = XMLUnit.buildTestDocument(result);
        XpathEngine xpath = XMLUnit.newXpathEngine();

        assertEquals("1", xpath.evaluate("count(//TileMapService[contains(@services,'http://localhost:8080/mycontext/')])", doc));
        assertEquals("2", xpath.evaluate("count(//TileMap[@title='mockLayer'])", doc));
        assertEquals("2", xpath.evaluate("count(//TileMap[@title='mockLayer'][@srs='EPSG:4326'])", doc));
        assertEquals("1", xpath.evaluate("count(//TileMap[@title='mockLayer'][contains(@href,'jpeg')])", doc));
        assertEquals("1", xpath.evaluate("count(//TileMap[@title='mockLayer'][contains(@href,'png')])", doc));
        assertEquals("0", xpath.evaluate("count(//TileMap[@title='mockLayer'][contains(@href,'jpeg-png')])", doc));
    }

    public void testTMSDocumentsWithCustomFactory() throws Exception {

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        service = new TMSService(sb, mock(RuntimeStats.class), gwcd, customFactory);
        
        @SuppressWarnings("unchecked")
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getPathInfo()).thenReturn("/service/tms/1.0.0");
        when(req.getRequestURI()).thenReturn("/mycontext/service/tms/1.0.0");
        when(req.getScheme()).thenReturn("http");
        when(req.getServerName()).thenReturn("localhost");
        when(req.getServerPort()).thenReturn(8080);
        when(req.getContextPath()).thenReturn("/mycontext");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/mycontext/service/tms/1.0.0"));
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);

        final String layerName = conv.getLayerId();
        assertNull(layerName);

        assertEquals(Conveyor.RequestHandler.SERVICE,conv.reqHandler);
        service.handleRequest(conv);

        String result = resp.getContentAsString();

        // Ensure the custom authorized Layer is contained and the un-authorized is not
        assertFalse(result.contains("customLayer1"));
        assertTrue(result.contains("customLayer2"));

        Document doc = XMLUnit.buildTestDocument(result);
        XpathEngine xpath = XMLUnit.newXpathEngine();

        //Note the https being added by the custom URLMangler
        assertEquals("1", xpath.evaluate("count(//TileMapService[contains(@services,'https://localhost:8080/mycontext/')])", doc));
        assertEquals("1", xpath.evaluate("count(//TileMap[@title='Custom Layer2'])", doc));
        assertEquals("1", xpath.evaluate("count(//TileMap[@title='Custom Layer2'][contains(@href,'jpeg-png')])", doc));
        assertEquals("0", xpath.evaluate("count(//TileMap[@title='Custom Layer1'])", doc));

        req = mock(HttpServletRequest.class);
        resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getScheme()).thenReturn("http");
        when(req.getServerName()).thenReturn("localhost");
        when(req.getServerPort()).thenReturn(8080);
        when(req.getContextPath()).thenReturn("/mycontext");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/mycontext/service/tms/1.0.0/customLayer2@EPSG:4326@jpeg-png"));
        when(req.getPathInfo()).thenReturn("/service/tms/1.0.0/customLayer2@EPSG:4326@jpeg-png");
        when(req.getRequestURI()).thenReturn("/mycontext/service/tms/1.0.0/customLayer2@EPSG:4326@jpeg-png");
        conv = service.getConveyor(req, resp);
        service.handleRequest(conv);
        result = resp.getContentAsString().replace("\n\n", "\n");
        doc = XMLUnit.buildTestDocument(result);
        xpath = XMLUnit.newXpathEngine();

        assertEquals("22", xpath.evaluate("count(//TileSet[contains(@href,'customLayer2')])", doc));

    }

    public void testGetTile() throws Exception {

        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        service = new TMSService(sb, mock(RuntimeStats.class), gwcd, customFactory);

        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        {
            List<String> gridSetNames = Arrays.asList("EPSG:4326");
            TileLayer tileLayer = mockTileLayer(customFactory.tld, customFactory.gsb, "customLayer2", gridSetNames, null);
            when(customFactory.tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        }

        // Sending a Tile request
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/mycontext/service/tms/1.0.0/customLayer2@EPSG%3A4326@jpeg-png/2/1/1.jpeg-png"));
        when(req.getPathInfo()).thenReturn("/service/tms/1.0.0/customLayer2@EPSG%3A4326@png/2/1/1.jpeg-png");
        when(req.getRequestURI()).thenReturn("/mycontext/service/tms/1.0.0/customLayer2@EPSG%3A4326@png/2/1/1.jpeg-png");
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getScheme()).thenReturn("http");
        when(req.getServerName()).thenReturn("localhost");
        when(req.getServerPort()).thenReturn(8080);
        when(req.getContextPath()).thenReturn("/mycontext");

        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        assertThat(conv, instanceOf(ConveyorTile.class));
    }
}
