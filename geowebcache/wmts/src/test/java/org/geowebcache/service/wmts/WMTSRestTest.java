/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Sandro Salari, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache.service.wmts;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathExists;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.ServerConfiguration;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.config.legends.LegendInfo;
import org.geowebcache.config.legends.LegendInfoBuilder;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileJSONProvider;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.meta.TileJSON;
import org.geowebcache.layer.meta.VectorLayerMetadata;
import org.geowebcache.mime.ApplicationMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.OWSException;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.ResponseUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;

public class WMTSRestTest {

    GridSetBroker broker =
            new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true)));
    private WMTSService wmtsService;
    private StorageBroker storageBroker;
    private TileLayerDispatcher tileLayerDispatcher;
    private SecurityDispatcher securityDispatcher;
    private RuntimeStats runtimeStats;

    @Before
    public void setupMockService() throws Exception {
        this.storageBroker = mock(StorageBroker.class);
        this.tileLayerDispatcher = tileLayerDispatcher();
        this.runtimeStats = mock(RuntimeStats.class);
        this.wmtsService =
                new WMTSService(storageBroker, tileLayerDispatcher, broker, runtimeStats);
        this.securityDispatcher = securityDispatcher();
        wmtsService.setSecurityDispatcher(securityDispatcher);
    }

    @BeforeClass
    public static void setupXMLUnit() {
        Map<String, String> namespaces = new HashMap<>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("ows", "http://www.opengis.net/ows/1.1");
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
    }

    @Test
    public void testGetCap() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setPathInfo("geowebcache/service/wmts/rest/WMTSCapabilities.xml");
        MockHttpServletResponse resp = dispatch(req);

        assertEquals(200, resp.getStatus());
        assertEquals("text/xml;charset=UTF-8", resp.getContentType());
        final Document doc = XMLUnit.buildTestDocument(resp.getContentAsString());
        assertXpathExists("//wmts:Contents/wmts:Layer", doc);
        assertXpathExists("//wmts:Contents/wmts:Layer[ows:Identifier='mockLayer']", doc);
        assertXpathEvaluatesTo(
                "2", "count(//wmts:Contents/wmts:Layer/wmts:Style/ows:Identifier)", doc);
        assertXpathExists("//wmts:Contents/wmts:Layer/wmts:Style[ows:Identifier='style-a']", doc);
        assertXpathExists(
                "//wmts:Contents/wmts:Layer/wmts:Style[ows:Identifier='style-b']/wmts:LegendURL"
                        + "[@width='125'][@height='130'][@format='image/png']"
                        + "[@minScaleDenominator='5000.0'][@maxScaleDenominator='10000.0']"
                        + "[@xlink:href='https://some-url?some-parameter=value3&another-parameter=value4']",
                doc);
        assertXpathExists(
                "//wmts:Contents/wmts:Layer/wmts:ResourceURL[@resourceType='tile']"
                        + "[@format='image/jpeg']"
                        + "[@template='http://localhost/service/wmts/rest/"
                        + "mockLayer/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/jpeg&time={time}&elevation={elevation}']",
                doc);
        assertXpathExists(
                "//wmts:Contents/wmts:Layer/wmts:ResourceURL[@resourceType='FeatureInfo']"
                        + "[@format='text/plain']"
                        + "[@template='http://localhost/service/wmts/rest"
                        + "/mockLayer/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=text/plain&time={time}&elevation={elevation}']",
                doc);
        assertXpathExists(
                "//wmts:ServiceMetadataURL[@xlink:href='http://localhost/service/wmts/rest"
                        + "/WMTSCapabilities.xml']",
                doc);
    }

    @Test
    public void testGetTileWithStyle() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setPathInfo(
                "geowebcache/service/wmts/rest/mockLayer/style-a/EPSG:4326/EPSG:4326:0/0/0");
        req.addParameter("format", "image/png");

        MockHttpServletResponse resp = dispatch(req);

        assertEquals(200, resp.getStatus());
        assertEquals("image/png", resp.getContentType());
        assertEquals("EPSG:4326", resp.getHeader("geowebcache-crs"));
        assertArrayEquals(getSampleTileContent().getContents(), resp.getContentAsByteArray());
    }

    @Test
    public void testGetTileJSONWithStyle() throws Exception {
        addTileLayerJsonMock("image/png");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setPathInfo("geowebcache/service/wmts/rest/mockLayerTileJSON/style-a/tilejson/png");
        req.addParameter("format", "application/json");
        MockHttpServletResponse resp = dispatch(req);

        assertEquals(200, resp.getStatus());
        String content = resp.getContentAsString();

        // Checking the response contains a tileUrl with the style
        assertTrue(
                content.contains(
                        "\"tiles\":[\"http://localhost/service/wmts/rest/mockLayerTileJSON/style-a/EPSG:900913/EPSG:900913:{z}/{y}/{x}?format=image/png\"]"));
        assertFalse(content.contains("vector_layers"));
    }

    @Test
    public void testGetTileJSONWithoutStyle() throws Exception {
        String mvt = ApplicationMime.mapboxVector.getFormat();
        addTileLayerJsonMock(mvt);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setPathInfo("geowebcache/service/wmts/rest/mockLayerTileJSON/tilejson/pbf");
        req.addParameter("format", ApplicationMime.json.getFormat());
        MockHttpServletResponse resp = dispatch(req);

        assertEquals(200, resp.getStatus());
        String content = resp.getContentAsString();

        // Checking the response contains a tileUrl without the style
        assertTrue(
                content.contains(
                        "\"tiles\":[\"http://localhost/service/wmts/rest/mockLayerTileJSON/EPSG:900913/EPSG:900913:{z}/{y}/{x}?format="
                                + mvt
                                + "\"]"));
        assertTrue(content.contains("vector_layers"));
    }

    private void addTileLayerJsonMock(String mimeType) throws GeoWebCacheException {

        final MimeType mimeType1 = MimeType.createFromFormat(mimeType);
        String layerNameJson = "mockLayerTileJSON";
        TileLayer tileLayerJson =
                mock(TileLayer.class, withSettings().extraInterfaces(TileJSONProvider.class));
        when(tileLayerDispatcher.getTileLayer(eq(layerNameJson))).thenReturn(tileLayerJson);
        when(tileLayerJson.getName()).thenReturn(layerNameJson);
        when(tileLayerJson.isEnabled()).thenReturn(true);
        when(tileLayerJson.isAdvertised()).thenReturn(true);
        when(tileLayerJson.getMimeTypes()).thenReturn(Arrays.asList(mimeType1));
        TileJSONProvider tileJSONProvider = (TileJSONProvider) tileLayerJson;
        when(tileJSONProvider.supportsTileJSON()).thenReturn(true);
        TileJSON json = new TileJSON();
        VectorLayerMetadata metadata = new VectorLayerMetadata();
        metadata.setFields(Collections.singletonMap("FIELD", "TYPE"));
        json.setLayers(Collections.singletonList(metadata));
        when(tileJSONProvider.getTileJSON()).thenReturn(json);

        String googleMercator = "EPSG:900913";
        when(tileLayerJson.getGridSubsets()).thenReturn(Collections.singleton(googleMercator));
        GridSubset subset = mock(GridSubset.class);
        when(subset.getGridNames()).thenReturn(new String[] {googleMercator + ":1"});
        when(tileLayerJson.getGridSubset(eq(googleMercator))).thenReturn(subset);

        when(tileLayerDispatcher.getLayerList()).thenReturn(Arrays.asList(tileLayerJson));
        when(tileLayerDispatcher.getLayerListFiltered()).thenReturn(Arrays.asList(tileLayerJson));
    }

    public MockHttpServletResponse dispatch(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        try {
            final Conveyor conveyor = wmtsService.getConveyor(req, resp);

            if (conveyor.reqHandler == Conveyor.RequestHandler.SERVICE) {
                final String layerName = conveyor.getLayerId();

                final TileLayer layer;
                if (Objects.nonNull(layerName)) {
                    layer = tileLayerDispatcher.getTileLayer(layerName);
                    if (layer != null && !layer.isEnabled()) {
                        throw new OWSException(
                                400,
                                "InvalidParameterValue",
                                "LAYERS",
                                "Layer '" + layerName + "' is disabled");
                    }
                    if (conveyor instanceof ConveyorTile) {
                        ((ConveyorTile) conveyor).setTileLayer(layer);
                    }
                }
                wmtsService.handleRequest(conveyor);
            } else {
                ResponseUtils.writeTile(
                        securityDispatcher(),
                        conveyor,
                        conveyor.getLayerId(),
                        tileLayerDispatcher(),
                        null,
                        runtimeStats);
            }
        } catch (OWSException e) {
            ResponseUtils.writeFixedResponse(
                    resp,
                    e.getResponseCode(),
                    e.getContentType(),
                    e.getResponse(),
                    Conveyor.CacheResult.OTHER,
                    runtimeStats);
        }

        return resp;
    }

    @Test
    public void testGetTileWithoutStyle() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setPathInfo("geowebcache/service/wmts/rest/mockLayer/EPSG:4326/EPSG:4326:0/0/0");
        req.addParameter("format", "image/png");

        final MockHttpServletResponse resp = dispatch(req);
        assertEquals(200, resp.getStatus());
    }

    @Test
    public void testGetTileWithEmptyStyle() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setPathInfo("geowebcache/service/wmts/rest/mockLayer//EPSG:4326/EPSG:4326:0/0/0");
        req.addParameter("format", "image/png");

        final MockHttpServletResponse resp = dispatch(req);
        assertEquals(200, resp.getStatus());
    }

    @Test
    public void testGetInfoWithStyle() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setPathInfo(
                "geowebcache/service/wmts/rest/mockLayer/style-a/EPSG:4326/EPSG:4326:0/0/0/0/0");
        req.addParameter("format", "text/plain");

        final MockHttpServletResponse resp = dispatch(req);

        assertEquals(200, resp.getStatus());
        assertEquals("text/plain", resp.getContentType());
    }

    @Test
    public void testGetInfoWithoutStyle() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setPathInfo("geowebcache/service/wmts/rest/mockLayer/EPSG:4326/EPSG:4326:0/0/0/0/0");
        req.addParameter("format", "text/plain");

        final MockHttpServletResponse resp = dispatch(req);

        assertEquals(200, resp.getStatus());
        assertEquals("text/plain", resp.getContentType());
    }

    @Test
    public void testOWSException() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setPathInfo("geowebcache/service/wmts/rest/mockLayer/EPSG:4326/EPSG:4326:0/0/0/0/0");
        req.addParameter("format", "text/none");

        MockHttpServletResponse resp = dispatch(req);

        assertEquals(HttpStatus.SC_BAD_REQUEST, resp.getStatus());
        assertEquals("text/xml", resp.getContentType());

        Document doc = XMLUnit.buildTestDocument(resp.getContentAsString());
        assertXpathExists(
                "//ows:ExceptionReport/ows:Exception[@exceptionCode='InvalidParameterValue']", doc);
    }

    @FunctionalInterface
    public interface TestToExecute {
        void execute() throws Exception;
    }

    @Test
    public void testGetCapabilitiesWithCiteValidation() throws Exception {
        testCiteValidationIsSuccessful(this::testGetCap);
    }

    @Test
    public void testGetTileWithCiteValidation() throws Exception {
        testCiteValidationIsSuccessful(
                () -> {
                    testGetTileWithStyle();
                    testGetTileWithoutStyle();
                });
    }

    @Test
    public void testGetInfoWithCiteValidation() throws Exception {
        testCiteValidationIsSuccessful(
                () -> {
                    testGetInfoWithStyle();
                    testGetInfoWithoutStyle();
                });
    }

    /** Helper method that just executes the provided test with CITE validation activated. */
    private void testCiteValidationIsSuccessful(TestToExecute request) throws Exception {
        // mock server configuration to activate CITE compliance checks
        ServerConfiguration configuration = mock(ServerConfiguration.class);
        when(configuration.isWmtsCiteCompliant()).thenReturn(true);
        ServerConfiguration previousConfiguration = wmtsService.getMainConfiguration();
        wmtsService.setMainConfiguration(configuration);
        try {
            // the following test should be successful
            request.execute();
        } finally {
            // set whatever was the previous server configuration used by WMTS service
            wmtsService.setMainConfiguration(previousConfiguration);
        }
    }

    public TileLayerDispatcher tileLayerDispatcher() throws Exception {
        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        List<String> gridSetNames =
                Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale", "EPSG:4326");
        String layerName = "mockLayer";
        TileLayer tileLayer = mock(TileLayer.class);

        StringParameterFilter styles = new StringParameterFilter();
        styles.setKey("STYLES");
        styles.setValues(Arrays.asList("style-a", "style-b"));

        StringParameterFilter time = new StringParameterFilter();
        time.setKey("time");
        time.setValues(Arrays.asList("2016-02-23T03:00:00.000Z"));

        StringParameterFilter elevation = new StringParameterFilter();
        elevation.setKey("elevation");
        elevation.setValues(Arrays.asList("500"));

        when(tileLayer.getParameterFilters()).thenReturn(Arrays.asList(styles, time, elevation));

        LegendInfo legendInfo2 =
                new LegendInfoBuilder()
                        .withStyleName("styla-b-legend")
                        .withWidth(125)
                        .withHeight(130)
                        .withFormat("image/png")
                        .withCompleteUrl(
                                "https://some-url?some-parameter=value3&another-parameter=value4")
                        .withMinScale(5000D)
                        .withMaxScale(10000D)
                        .build();
        when(tileLayer.getLayerLegendsInfo())
                .thenReturn(Collections.singletonMap("style-b", legendInfo2));

        when(tld.getTileLayer(eq(layerName))).thenReturn(tileLayer);
        when(tileLayer.getName()).thenReturn(layerName);
        when(tileLayer.isEnabled()).thenReturn(true);
        when(tileLayer.isAdvertised()).thenReturn(true);

        final MimeType mimeType1 = MimeType.createFromFormat("image/png");
        final MimeType mimeType2 = MimeType.createFromFormat("image/jpeg");
        when(tileLayer.getMimeTypes()).thenReturn(Arrays.asList(mimeType1, mimeType2));

        final MimeType infoMimeType1 = MimeType.createFromFormat("text/plain");
        final MimeType infoMimeType2 = MimeType.createFromFormat("text/html");
        final MimeType infoMimeType3 = MimeType.createFromFormat("application/vnd.ogc.gml");
        when(tileLayer.getInfoMimeTypes())
                .thenReturn(Arrays.asList(infoMimeType1, infoMimeType2, infoMimeType3));
        Map<String, GridSubset> subsets = new HashMap<>();
        Map<SRS, List<GridSubset>> bySrs = new HashMap<>();

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

        when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        when(tld.getLayerListFiltered()).thenReturn(Arrays.asList(tileLayer));

        when(tileLayer.getTile(any(ConveyorTile.class)))
                .thenAnswer(
                        (Answer<ConveyorTile>)
                                invocation -> {
                                    ConveyorTile sourceTile =
                                            (ConveyorTile) invocation.getArguments()[0];
                                    sourceTile.setBlob(getSampleTileContent());
                                    return sourceTile;
                                });

        when(tileLayer.getFeatureInfo(
                        any(ConveyorTile.class),
                        any(BoundingBox.class),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt()))
                .thenAnswer((Answer<Resource>) invocation -> new ByteArrayResource(new byte[0]));

        return tld;
    }

    public ByteArrayResource getSampleTileContent() throws IOException, URISyntaxException {
        return new ByteArrayResource(
                FileUtils.readFileToByteArray(
                        new File(getClass().getResource("/image.png").toURI())));
    }

    @Bean
    public SecurityDispatcher securityDispatcher() {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(false);
        return secDisp;
    }
}
