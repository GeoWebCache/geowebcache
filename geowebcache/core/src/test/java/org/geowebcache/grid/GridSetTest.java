package org.geowebcache.grid;

import java.util.Arrays;

import junit.framework.TestCase;

public class GridSetTest extends TestCase {

    GridSetBroker gridSetBroker = new GridSetBroker(false, false);

    // Top left
    GridSet gridSetTL = GridSetFactory.createGridSet("test", SRS.getEPSG4326(),
            BoundingBox.WORLD4326, true, 10, null, 0.00028, 256, 256, false);

    // Bottom left
    GridSet gridSetBL = GridSetFactory.createGridSet("test", SRS.getEPSG4326(),
            BoundingBox.WORLD4326, false, 10, null, 0.00028, 256, 256, false);

    public void testBoundsFromIndex() throws Exception {
        long[] index = { 0, 0, 1 };
        BoundingBox bboxTL = gridSetTL.boundsFromIndex(index);
        BoundingBox bboxBL = gridSetBL.boundsFromIndex(index);
        BoundingBox solution = new BoundingBox(-180.0, -90.0, -90.0, 0);

        assertTrue(bboxTL.equals(solution));
        assertTrue(bboxBL.equals(solution));
    }

    public void testBoundsFromRectangle() throws Exception {
        long[] rect = { 0, 0, 0, 0, 0 };
        BoundingBox bboxTL = gridSetTL.boundsFromRectangle(rect);
        BoundingBox bboxBL = gridSetBL.boundsFromRectangle(rect);
        BoundingBox solution = new BoundingBox(-180.0, -90.0, 0.0, 90.0);

        assertEquals(solution, bboxTL);
        assertEquals(solution, bboxBL);

        long[] rect2 = { 2, 1, 2, 1, 1 };
        BoundingBox bboxTL2 = gridSetTL.boundsFromRectangle(rect2);
        BoundingBox bboxBL2 = gridSetBL.boundsFromRectangle(rect2);
        BoundingBox solution2 = new BoundingBox(0.0, 0.0, 90.0, 90.0);

        assertEquals(solution2, bboxBL2);
        assertEquals(solution2, bboxTL2);
    }

    public void testClosestIndex() throws Exception {
        BoundingBox box = new BoundingBox(-180.0, -90.0, -90.0, 0);
        long[] idxTL = gridSetTL.closestIndex(box);
        long[] idxBL = gridSetBL.closestIndex(box);
        long[] solution = { 0, 0, 1 };

        assertTrue(Arrays.equals(idxTL, solution));
        assertTrue(Arrays.equals(idxBL, solution));
    }

    public void testClosestRectangle() throws Exception {
        BoundingBox box = new BoundingBox(-180.0, -90.0, 0.0, 0.0);
        long[] rectTL = gridSetTL.closestRectangle(box);
        long[] rectBL = gridSetBL.closestRectangle(box);
        long[] solution = { 0, 0, 1, 0, 1 };

        assertTrue(Arrays.equals(rectTL, solution));
        assertTrue(Arrays.equals(rectBL, solution));
    }

    public void testGetLeftTopCorner() throws Exception {
        double[] tlTL = gridSetTL.getOrderedTopLeftCorner(1);
        double[] tlBL = gridSetBL.getOrderedTopLeftCorner(1);

        assertTrue(Math.abs(tlBL[1] - 90.0) < 0.01);
        assertTrue(Math.abs(tlTL[1] - 90.0) < 0.01);
    }
}