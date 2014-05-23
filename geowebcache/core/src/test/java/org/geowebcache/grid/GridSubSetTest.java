package org.geowebcache.grid;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.seed.GWCTask.TYPE;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.TileRange;
import org.geowebcache.util.MockLockProvider;
import org.junit.Test;

/**
 * Simple Test class for testing the behavior of a {@link GridSubset} with a non-zero 
 * zoomStart parameter.
 */
public class GridSubSetTest {

    @Test
    public void testCreateTileRange() throws IOException, GeoWebCacheException {
        // Creation of a Test Layer
        WMSLayer tl = createWMSLayer();
        // First SubSet string
        String gridSet = tl.getGridSubsets().iterator().next();
        // Get the subset associated to the name
        GridSubset gridSubSet = tl.getGridSubset(gridSet);
        // Simple Bounding Box for the tests
        BoundingBox bounds = new BoundingBox(0d, 0d, 1d, 1d);
        // Selection of the area that covers the given rectangle
        long[][] result = gridSubSet.getCoverageIntersections(bounds);
        // This assertion should be true if no Exception has been thrown
        assertNotNull(result);
    } 
    
    /**
     * Creation of a PNG test Layer with a non-zero zoomStart parameter for the test.
     * 
     * @param format
     * @return
     */
    private static WMSLayer createWMSLayer() {
        // Subsets table
        Hashtable<String, GridSubset> grids = new Hashtable<String, GridSubset>();
        // GridSetBroker to use for creating a GridSubSet to add to the table
        GridSetBroker gridSetBroker = new GridSetBroker(false, false);
        
        // Creation of a GridSubSet with a non-zero zoomStart parameter 
        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326,
                new BoundingBox(-30.0, 15.0, 45.0, 30), 5, 10, null, null);

        grids.put(grid.getName(), grid);
        
        // Parameters for the WMS Layer
        String[] urls = { "http://localhost:38080/wms" };
        List<String> formatList = Collections.singletonList("image/png");        
        int[] metaWidthHeight = { 3, 3 };
        // WMS layer creation
        WMSLayer layer = new WMSLayer("test:layer", urls, "aStyle", "test:layer", formatList,
                grids, null, metaWidthHeight, "vendorparam=true", false, null);
        // Layer initialization
        layer.initialize(gridSetBroker);
        layer.setLockProvider(new MockLockProvider());

        return layer;
    }
}
