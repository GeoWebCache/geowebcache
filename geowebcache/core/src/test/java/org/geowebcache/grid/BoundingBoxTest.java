package org.geowebcache.grid;

import java.util.Arrays;

import junit.framework.TestCase;

public class BoundingBoxTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Verifies that this functions output remains as expected, since we communicate a lot using
     * strings.
     * 
     * Ff not you should figure out where it is used.
     * 
     * @throws Exception
     */
    public void testBBOX() throws Exception {
        BoundingBox bbox = new BoundingBox(-180.0, -90.0, 180.0, 90.0);
        assert (bbox.isSane());

        String bboxStr = bbox.toString();
        if (bboxStr.equalsIgnoreCase("-180.0,-90.0,180.0,90.0")) {
            assertTrue(true);
        } else {
            assertTrue(false);
        }
    }

    public void testBBOXScale() throws Exception {
        BoundingBox bbox = new BoundingBox(-180.0, -90.0, 180.0, 90.0);

        BoundingBox copy = new BoundingBox(bbox);
        bbox.scale(1.0);
        bbox.scale(0.5);
        bbox.scale(2.0);

        assert (bbox.isSane());
        assert (bbox.equals(copy));
    }

    public void testIntersection() throws Exception {
        BoundingBox bb1 = new BoundingBox(0, 0, 10, 10);
        BoundingBox bb2 = new BoundingBox(5, 5, 20, 20);

        BoundingBox intersection = BoundingBox.intersection(bb1, bb2);
        assertNotNull(intersection);
        assertEquals(5D, intersection.getWidth());
        assertEquals(5D, intersection.getHeight());
        assertTrue(intersection.isSane());
        assertTrue(Arrays.equals(new double[] { 5, 5, 10, 10 }, intersection.getCoords()));
    }

    /**
     * Two bboxes don't intersect, BoundingBox.intersection()'s result should be the empty bbox
     * 
     * @throws Exception
     */
    public void testIntersectionNonIntersecting() throws Exception {
        BoundingBox bb1 = new BoundingBox(0, 0, 10, 10);
        BoundingBox bb2 = new BoundingBox(11, 11, 20, 20);

        BoundingBox intersection = BoundingBox.intersection(bb1, bb2);
        assertNotNull(intersection);
        assertTrue(intersection.isNull());
        assertFalse(intersection.isSane());
    }
}