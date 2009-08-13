package org.geowebcache.grid;

import java.util.Arrays;

import junit.framework.TestCase;

import org.geowebcache.util.wms.BBOX;

public class GridSubSetFactoryTest extends TestCase {
    
    public void testCoverageBounds() throws Exception {
        BBOX bbox = new BBOX(0, 0, 180, 90);
        
        GridSubSet grid = GridSubSetFactory.createGridSubSet(
                GridSetBroker.WORLD_EPSG4326, bbox, 0, 0);
        
        long[] ret = grid.getCoverage(0);
        long[] correct = {1, 0, 1, 0, 0};
        
        this.assertTrue(Arrays.equals(correct, ret));
    }
    
    public void testCoverageBounds2() throws Exception {
        BBOX bbox = new BBOX(0, 0, 180, 90);
        
        GridSubSet grid = GridSubSetFactory.createGridSubSet(
                GridSetBroker.WORLD_EPSG4326, bbox, 0, 1);
        
        long[] ret = grid.getCoverage(1);
        long[] correct = { 2, 1, 3, 1, 1 };
        
        this.assertTrue(Arrays.equals(correct, ret));
    }
}
