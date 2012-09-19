package org.geowebcache.service.wms;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeType;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;

public class WMSServiceTest extends TestCase {

    private WMSService service;

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private GridSetBroker gridsetBroker;

    protected void setUp() throws Exception {
        sb = mock(StorageBroker.class);
        tld = mock(TileLayerDispatcher.class);
        gridsetBroker = new GridSetBroker(true, true);
    }

    protected void tearDown() throws Exception {
    }

    /**
     * Layer may be configured with mutliple GridSets for the same CRS, and should chose the best
     * fit for the request
     */
    public void testGetConveyorMultipleCrsMatchingGridSubsets() throws Exception {

        testMultipleCrsMatchingGridSubsets("EPSG:4326", "EPSG:4326", new long[] { 1, 1, 1 });
        testMultipleCrsMatchingGridSubsets("EPSG:4326", "EPSG:4326", new long[] { 10, 10, 10 });

        testMultipleCrsMatchingGridSubsets("EPSG:4326", "GlobalCRS84Scale", new long[] { 1, 1, 1 });
        testMultipleCrsMatchingGridSubsets("EPSG:4326", "GlobalCRS84Scale",
                new long[] { 10, 10, 10 });

        testMultipleCrsMatchingGridSubsets("EPSG:4326", "GlobalCRS84Scale", new long[] { 1, 1, 1 });
        testMultipleCrsMatchingGridSubsets("EPSG:4326", "GlobalCRS84Scale",
                new long[] { 10, 10, 10 });

    }

    private void testMultipleCrsMatchingGridSubsets(final String srs, final String expectedGridset,
            long[] tileIndex) throws Exception {

        service = new WMSService(sb, tld, mock(RuntimeStats.class));

        @SuppressWarnings("unchecked")
        Map<String, String> kvp = new CaseInsensitiveMap();
        kvp.put("format", "image/png");
        kvp.put("srs", "EPSG:4326");
        kvp.put("width", "256");
        kvp.put("height", "256");
        kvp.put("layers", "mockLayer");
        kvp.put("tiled", "true");
        kvp.put("request", "GetMap");

        List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale",
                "EPSG:4326");
        TileLayer tileLayer = mockTileLayer("mockLayer", gridSetNames);

        // make the request match a tile in the expected gridset
        BoundingBox bounds;
        bounds = tileLayer.getGridSubset(expectedGridset).boundsFromIndex(tileIndex);
        kvp.put("bbox", bounds.toString());

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(kvp);

        ConveyorTile tileRequest = service.getConveyor(req, resp);
        assertNotNull(tileRequest);

        assertEquals(expectedGridset, tileRequest.getGridSetId());
        assertEquals("image/png", tileRequest.getMimeType().getMimeType());
        assertTrue(
                "Expected " + Arrays.toString(tileIndex) + " got "
                        + Arrays.toString(tileRequest.getTileIndex()),

                Arrays.equals(tileIndex, tileRequest.getTileIndex()));
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

}
