package org.geowebcache.layer.wms;

import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.mime.ImageMime;

public class MetaTileTest extends TestCase {

    GridSetBroker gridSetBroker = new GridSetBroker(false, false);
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void test1MetaTile() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);
        int metaHeight = 1;
        int metaWidth = 1;

        GridSubset grid = GridSubsetFactory.createGridSubSet(
                gridSetBroker.WORLD_EPSG4326,
                bbox,
                0,
                30);
        
        long[] gridPos = { 0, 0, 0 };
        
        //int[] gridBounds, int[] tileGridPosition, int metaX, int metaY
        WMSMetaTile mt = new WMSMetaTile(
                null, grid, ImageMime.png, null,
                gridPos, metaWidth, metaHeight, Collections.singletonMap("test", "test1"));

        long[] solution = { 0, 0, 0, 0, 0 };
        boolean test = Arrays.equals(mt.getMetaTileGridBounds(), solution);
        if (!test) {
            System.out.println("1 - " + mt.debugString());
            System.out.println("test1MetaTile {" + Arrays.toString(solution)
                    + "} {" + Arrays.toString(mt.getMetaTileGridBounds()) + "}");
        }
        assertTrue(test);
    }

    public void test2MetaTile() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);
        int metaHeight = 3;
        int metaWidth = 3;

        GridSubset grid = GridSubsetFactory.createGridSubSet(
                gridSetBroker.WORLD_EPSG4326,
                bbox,
                0,
                30);
        
        long[] gridPos = { 127, 63, 6 };
        WMSMetaTile mt = new WMSMetaTile(
                    null, grid, ImageMime.png, null,
                    gridPos, metaWidth, metaHeight, Collections.singletonMap("test", "test1"));

        long[] solution = { 126, 63, 127, 63, 6 };
        boolean test = Arrays.equals(mt.getMetaTileGridBounds(), solution);
        if (!test) {
            System.out.println("2 - " + mt.debugString());
            System.out.println("test2MetaTile {" + Arrays.toString(solution)
                    + "} {" + Arrays.toString(mt.getMetaTileGridBounds()) + "}");
        }
        assertTrue(test);
    }

    public void test3MetaTile() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 20037508.34, 20037508.34);
        int metaHeight = 1;
        int metaWidth = 1;
        
        GridSubset grid = GridSubsetFactory.createGridSubSet(
                gridSetBroker.WORLD_EPSG3857,
                bbox,
                0,
                30);
          
        long[] gridPos = { 0, 0, 0 };
        WMSMetaTile mt = new WMSMetaTile(
                null, grid, ImageMime.png, null, 
                gridPos, metaWidth, metaHeight, Collections.singletonMap("test", "test1"));
        
        long[] solution = { 0, 0, 0, 0, 0 };
        boolean test = Arrays.equals(mt.getMetaTileGridBounds(), solution);
        if (!test) {
            System.out.println("3 - " + mt.debugString());
            System.out.println("test3MetaTile {" + Arrays.toString(solution)
                    + "} {" + Arrays.toString(mt.getMetaTileGridBounds()) + "}");
        }
        assertTrue(test);
    }

    public void test4MetaTile() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 20037508.34, 20037508.34);
        
        int metaHeight = 3;
        int metaWidth = 3;
        
        GridSubset grid = GridSubsetFactory.createGridSubSet(
                gridSetBroker.WORLD_EPSG3857,
                bbox,
                0,
                30);
        
        
        long[] gridPos = { 70, 70, 6 };
        WMSMetaTile mt = new WMSMetaTile(
                null, grid, ImageMime.png, null, 
        	gridPos, metaWidth, metaHeight, Collections.singletonMap("test", "test1"));
        
        long[] solution = { 69, 69, 63, 63, 6 };
        boolean test = Arrays.equals(mt.getMetaTileGridBounds(), solution);
        if (test) {

        } else {
            System.out.println("4 - " + mt.debugString());
            System.out.println("test4MetaTile {" + Arrays.toString(solution)
                    + "} {" + Arrays.toString(mt.getMetaTileGridBounds()) + "}");
        }
        assertTrue(test);
    }
    
    /**
     * 
     * @throws Exception
     */
    public void test5MetaTileGutter() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);
        
        WMSLayer layer = createWMSLayer(bbox);

        GridSubset grid = GridSubsetFactory.createGridSubSet(
                gridSetBroker.WORLD_EPSG4326,
                bbox,
                0,
                30);
        
        // Set the gutter
        layer.gutter = 50;

        // Lets make a tile close to the edge, this should only have a gutter to west / south
        long[] gridPos = { 127, 63, 6 };
        WMSMetaTile mt = new WMSMetaTile(
                    layer, grid, ImageMime.png, null, 
                    gridPos, layer.getMetaTilingFactors()[0], 
                    layer.getMetaTilingFactors()[1], Collections.singletonMap("test", "test1"));

        // The actual gutter is calculated right at construction time
        Map<String, String> wmsParams = mt.getWMSParams();
        assertEquals(layer.gutter.intValue(), mt.getGutter()[0]);
        assertEquals(layer.gutter.intValue(), mt.getGutter()[1]);
        assertEquals(0, mt.getGutter()[2]);
        assertEquals(0, mt.getGutter()[3]);

        int height = Integer.parseInt(wmsParams.get("HEIGHT"));
        
        //assertEquals(height, 256 + 50);

        long[] midGridPos = { 83, 45, 6 };
        mt = new WMSMetaTile(
                    layer, grid, ImageMime.png, null, 
                    midGridPos, layer.getMetaTilingFactors()[0], 
                    layer.getMetaTilingFactors()[1], Collections.singletonMap("test", "test1"));

        // The actual gutter is calculated right at construction time
        wmsParams = mt.getWMSParams();
        assertTrue(mt.getGutter()[0] == layer.gutter);
        assertTrue(mt.getGutter()[1] == layer.gutter);
        assertTrue(mt.getGutter()[2] == layer.gutter);
        assertTrue(mt.getGutter()[3] == layer.gutter);
        
        height = Integer.parseInt(wmsParams.get("HEIGHT"));
        
        assertEquals(height, 768 + 2*50);
        
        String[] coordStrs = wmsParams.get("BBOX").split(",");
        
        // Lets check some specific coordinates too
        assertTrue(Math.abs( Double.parseDouble(coordStrs[0]) - 47.26318359375) < 0.001);   
        assertTrue(Math.abs( Double.parseDouble(coordStrs[3]) - 45.54931640625) < 0.001);
    }
    
    private WMSLayer createWMSLayer(BoundingBox layerBounds) {
        String[] urls = {"http://localhost:38080/wms"};
        List<String> formatList = new LinkedList<String>();
        formatList.add("image/png");
        
        Hashtable<String,GridSubset> grids = new Hashtable<String,GridSubset>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326);
        
        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = {3,3};
        
        WMSLayer layer = new WMSLayer("test:layer", urls, "aStyle", "test:layer", formatList, grids, null, metaWidthHeight, "vendorparam=true", false);
        
        layer.initialize(gridSetBroker);
        
        return layer;
    }
}