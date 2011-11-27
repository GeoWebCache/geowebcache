package org.geowebcache.grid;

import junit.framework.TestCase;


public class GridSetFactoryTest extends TestCase {

    public void testResolutionsArrayEPSG4326() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 90);
        double[] resolutions = {180.0/256, 180.0/512, 180.0/1024, 180.0/2048};
        
        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false, resolutions, null, null, 0.00028, null, 256, 256, false);
        
        assertEquals("test", gridSet.getName());
        assertEquals(-180.0, gridSet.getBaseCoords()[0]);
        assertEquals(-90.0, gridSet.getBaseCoords()[1]);
        assertEquals(resolutions.length, gridSet.getGridLevels().length);
        
        Grid grid0 = gridSet.getGridLevels()[0];
        
        assertEquals(180.0/256, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGridLevels()[3];
        
        assertEquals(180.0/2048, grid3.getResolution());
        assertEquals((long) Math.pow(2, 4), grid3.getNumTilesWide());
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }

    public void testResolutionsArrayEPSG3785() throws Exception {
        BoundingBox extent = new BoundingBox(-20037508.34,-20037508.34,20037508.34,20037508.34);
        double[] resolutions = {(20037508.34*2)/256, (20037508.34*2)/512, (20037508.34*2)/1024, (20037508.34*2)/2048};
        
        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG3857(), extent, false, resolutions, null, null, 0.00028, null, 256, 256, false);
        
        Grid grid0 = gridSet.getGridLevels()[0];
        
        assertEquals((20037508.34*2)/256, grid0.getResolution());
        assertEquals(1L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());
        
        Grid grid3 = gridSet.getGridLevels()[3];
        
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesWide());
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }
    
    public void testResolutionsArrayTricky1() throws Exception {
        // This should be expanded in the X direction
        BoundingBox extent = new BoundingBox(-173, -90, 180, 96);
        double[] resolutions = {180.0/200, 180.0/400, 180.0/800};
        
        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false, resolutions, null, null, 0.00028, null, 200, 200, false);
        
        assertEquals(-173.0, gridSet.getBaseCoords()[0]);
        assertEquals(-90.0, gridSet.getBaseCoords()[1]);
        assertEquals(resolutions.length, gridSet.getGridLevels().length);
        
        Grid grid0 = gridSet.getGridLevels()[0];
        
        assertEquals(180.0/200, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(2L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGridLevels()[2];
        
        assertEquals(180.0/800, grid3.getResolution());
        assertEquals(8L, grid3.getNumTilesWide());
        assertEquals(5L, grid3.getNumTilesHigh());
        
    }
    
    public void testBoundingBoxEPSG4326() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 90);
        
        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false, 4, null, 0.00028, 256, 256, false);
        
        assertEquals("test", gridSet.getName());
        assertEquals(-180.0, gridSet.getBaseCoords()[0]);
        assertEquals(-90.0, gridSet.getBaseCoords()[1]);
        assertEquals(4, gridSet.getGridLevels().length);
        
        Grid grid0 = gridSet.getGridLevels()[0];
        
        assertEquals(180.0/256, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGridLevels()[3];
        
        assertEquals(180.0/2048, grid3.getResolution());
        assertEquals((long) Math.pow(2, 4), grid3.getNumTilesWide());
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }
    
    public void testBoundingBoxEPSG3785() throws Exception {
        BoundingBox extent = new BoundingBox(-20037508.34,-20037508.34,20037508.34,20037508.34);
        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG3857(), extent, false, 6, null, 0.00028, 256, 256, false);
        
        Grid grid0 = gridSet.getGridLevels()[0];
        
        assertEquals((20037508.34*2)/256, grid0.getResolution());
        assertEquals(1L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());
        
        Grid grid3 = gridSet.getGridLevels()[3];
        
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesWide());
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }
    
    public void testBoundingBoxTricky1() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 172, 90);
        
        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false, 4, null, 0.00028, 256, 256, false);
        
        assertEquals("test", gridSet.getName());
        assertEquals(-180.0, gridSet.getBaseCoords()[0]);
        assertEquals(-90.0, gridSet.getBaseCoords()[1]);
        assertEquals(4, gridSet.getGridLevels().length);
        
        Grid grid0 = gridSet.getGridLevels()[0];
        
        assertEquals(180.0/256, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());
    }
    
    public void testBoundingBoxTricky2() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 82);
        
        SRS srs = SRS.getEPSG4326();
        
        GridSet gridSet = GridSetFactory.createGridSet("test", srs, extent, false, 4, null, 0.00028, 256, 256, false);
        
        assertEquals("test", gridSet.getName());
        assertEquals(-180.0, gridSet.getBaseCoords()[0]);
        assertEquals(-90.0, gridSet.getBaseCoords()[1]);
        assertEquals(4, gridSet.getGridLevels().length);
        
        Grid grid0 = gridSet.getGridLevels()[0];
        
        assertEquals(180.0/256, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());
    }
}