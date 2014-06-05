package org.geowebcache.service.wmts;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import junit.framework.TestCase;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.custommonkey.xmlunit.Validator;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.conveyor.Conveyor;
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
import org.geowebcache.util.NullURLMangler;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.mockrunner.mock.web.MockHttpServletResponse;

public class WMTSServiceTest extends TestCase {

    private WMTSService service;

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private GridSetBroker gridsetBroker;

    protected void setUp() throws Exception {
        sb = mock(StorageBroker.class);
        tld = mock(TileLayerDispatcher.class);
        gridsetBroker = new GridSetBroker(true, true);
    }

    private TileLayer mockTileLayer(String layerName, List<String> gridSetNames, List<ParameterFilter> parameterFilters) throws Exception {

        TileLayer tileLayer = mock(TileLayer.class);
        when(tld.getTileLayer(eq(layerName))).thenReturn(tileLayer);
        when(tileLayer.getName()).thenReturn(layerName);
        when(tileLayer.isEnabled()).thenReturn(true);

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

    public void testGetCap() throws Exception {
    
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        service = new WMTSService(sb, tld,null , mock(RuntimeStats.class));
    
        @SuppressWarnings("unchecked")
        Map<String, String> kvp = new CaseInsensitiveMap();
        kvp.put("service", "WMS");
        kvp.put("version", "1.1.1");
        kvp.put("request", "GetCapabilities");
       
    
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        
        
        {
            List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326");
            
            TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames, Collections.<ParameterFilter>emptyList());
            when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        }
    
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        
        final String layerName = conv.getLayerId();
        assertNull(layerName);
        
        assertEquals(Conveyor.RequestHandler.SERVICE,conv.reqHandler);
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld,gridsetBroker, conv.servletReq,"http://localhost:8080", "/service/wms", new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp,mock(RuntimeStats.class));   
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml", resp.getHeader("content-disposition"));                            
    
        // System.out.println(resp.getOutputStreamContent());
        
        String result = resp.getOutputStreamContent();
        
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
    
    public void testGetCapUnboundedStyleFilter() throws Exception {
        
        GeoWebCacheDispatcher gwcd = mock(GeoWebCacheDispatcher.class);
        when(gwcd.getServletPrefix()).thenReturn(null);
        
        service = new WMTSService(sb, tld,null , mock(RuntimeStats.class));
    
        @SuppressWarnings("unchecked")
        Map<String, String> kvp = new CaseInsensitiveMap();
        kvp.put("service", "WMS");
        kvp.put("version", "1.1.1");
        kvp.put("request", "GetCapabilities");
       
    
        HttpServletRequest req = mock(HttpServletRequest.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);
        
        
        {
            List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326");
            
            ParameterFilter styleFilter = mock(ParameterFilter.class);
            when(styleFilter.getKey()).thenReturn("STYLE");
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
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld,gridsetBroker, conv.servletReq,"http://localhost:8080", "/service/wms", new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp,mock(RuntimeStats.class));   
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml", resp.getHeader("content-disposition"));                            
    
        // System.out.println(resp.getOutputStreamContent());
        
        String result = resp.getOutputStreamContent();
        
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

}
