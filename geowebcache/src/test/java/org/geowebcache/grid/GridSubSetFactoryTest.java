package org.geowebcache.grid;

import java.util.Arrays;

import junit.framework.TestCase;


public class GridSubSetFactoryTest extends TestCase {
    
    GridSetBroker gridSetBroker = new GridSetBroker(false);
    
    public void testCoverageBounds() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);
        
        GridSubSet grid = GridSubSetFactory.createGridSubSet(
                gridSetBroker.WORLD_EPSG4326, bbox, 0, 0);
        
        long[] ret = grid.getCoverage(0);
        long[] correct = {1, 0, 1, 0, 0};
        
        assertTrue(Arrays.equals(correct, ret));
    }
    
    public void testCoverageBounds2() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);
        
        GridSubSet grid = GridSubSetFactory.createGridSubSet(
                gridSetBroker.WORLD_EPSG4326, bbox, 0, 1);
        
        long[] ret = grid.getCoverage(1);
        long[] correct = { 2, 1, 3, 1, 1 };
        
        assertTrue(Arrays.equals(correct, ret));
    }
}
