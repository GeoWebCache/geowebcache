package org.geowebcache.service.wmts;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import junit.framework.TestCase;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.conveyor.Conveyor;
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

    private TileLayer mockTileLayer(String layerName, List<String> gridSetNames) throws Exception {

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
        
        

        List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale","EPSG:4326");
        TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames);
        when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));
        
    
        Conveyor conv = service.getConveyor(req, resp);
        assertNotNull(conv);
        
        final String layerName = conv.getLayerId();
        assertNull(layerName);
        
        assertEquals(Conveyor.RequestHandler.SERVICE,conv.reqHandler);
        WMTSGetCapabilities wmsCap = new WMTSGetCapabilities(tld,gridsetBroker, conv.servletReq,"http://localhost:8080", "/service/wms", new NullURLMangler());
        wmsCap.writeResponse(conv.servletResp,mock(RuntimeStats.class));   
        assertTrue(resp.containsHeader("content-disposition"));
        assertEquals("inline;filename=wmts-getcapabilities.xml", resp.getHeader("content-disposition"));                            
    
    }

}
