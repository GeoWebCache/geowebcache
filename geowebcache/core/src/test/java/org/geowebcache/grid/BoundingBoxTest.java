package org.geowebcache.grid;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BoundingBoxTest {

    @Before
    public void setUp() throws Exception {}

    /**
     * Verifies that this functions output remains as expected, since we communicate a lot using strings.
     *
     * <p>Ff not you should figure out where it is used.
     */
    @Test
    public void testBBOX() throws Exception {
        BoundingBox bbox = new BoundingBox(-180.0, -90.0, 180.0, 90.0);
        assert (bbox.isSane());

        String bboxStr = bbox.toString();
        if (bboxStr.equalsIgnoreCase("-180.0,-90.0,180.0,90.0")) {
            Assert.assertTrue(true);
        } else {
            Assert.fail();
        }
    }

    @Test
    public void testBBOXScale() throws Exception {
        BoundingBox bbox = new BoundingBox(-180.0, -90.0, 180.0, 90.0);

        BoundingBox copy = new BoundingBox(bbox);
        bbox.scale(1.0);
        bbox.scale(0.5);
        bbox.scale(2.0);

        assert (bbox.isSane());
        assert (bbox.equals(copy));
    }

    @Test
    public void testIntersection() throws Exception {
        BoundingBox bb1 = new BoundingBox(0, 0, 10, 10);
        BoundingBox bb2 = new BoundingBox(5, 5, 20, 20);

        BoundingBox intersection = BoundingBox.intersection(bb1, bb2);
        Assert.assertNotNull(intersection);
        Assert.assertEquals(5D, intersection.getWidth(), 0d);
        Assert.assertEquals(5D, intersection.getHeight(), 0d);
        Assert.assertTrue(intersection.isSane());
        assertArrayEquals(new double[] {5, 5, 10, 10}, intersection.getCoords(), 0.0);
    }

    /** Two bboxes don't intersect, BoundingBox.intersection()'s result should be the empty bbox */
    @Test
    public void testIntersectionNonIntersecting() throws Exception {
        BoundingBox bb1 = new BoundingBox(0, 0, 10, 10);
        BoundingBox bb2 = new BoundingBox(11, 11, 20, 20);

        BoundingBox intersection = BoundingBox.intersection(bb1, bb2);
        Assert.assertNotNull(intersection);
        Assert.assertTrue(intersection.isNull());
        Assert.assertFalse(intersection.isSane());
    }
}
