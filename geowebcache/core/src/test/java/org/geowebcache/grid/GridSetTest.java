package org.geowebcache.grid;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.util.Collections;
import org.geowebcache.config.DefaultGridsets;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;

public class GridSetTest {

    GridSetBroker gridSetBroker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));

    // Top left
    GridSet gridSetTL = GridSetFactory.createGridSet(
            "test", SRS.getEPSG4326(), BoundingBox.WORLD4326, true, 10, null, 0.00028, 256, 256, false);

    // Bottom left
    GridSet gridSetBL = GridSetFactory.createGridSet(
            "test", SRS.getEPSG4326(), BoundingBox.WORLD4326, false, 10, null, 0.00028, 256, 256, false);
    // Top left
    GridSet gridSetTLswap = GridSetFactory.createGridSet(
            "test", SRS.getEPSG4326(), BoundingBox.WORLD4326, true, 10, null, 0.00028, 256, 256, true);

    // Bottom left
    GridSet gridSetBLswap = GridSetFactory.createGridSet(
            "test", SRS.getEPSG4326(), BoundingBox.WORLD4326, false, 10, null, 0.00028, 256, 256, true);

    Matcher<BoundingBox> closeTo(BoundingBox expected, double error) {
        return allOf(
                hasProperty("minX", Matchers.closeTo(expected.getMinX(), error)),
                hasProperty("minY", Matchers.closeTo(expected.getMinY(), error)),
                hasProperty("maxX", Matchers.closeTo(expected.getMaxX(), error)),
                hasProperty("maxY", Matchers.closeTo(expected.getMaxY(), error)));
    }

    @Test
    public void testBoundsFromIndex() throws Exception {
        long[] index = {0, 0, 1};
        BoundingBox bboxTL = gridSetTL.boundsFromIndex(index);
        BoundingBox bboxBL = gridSetBL.boundsFromIndex(index);
        BoundingBox bboxTLswap = gridSetTLswap.boundsFromIndex(index);
        BoundingBox bboxBLswap = gridSetBLswap.boundsFromIndex(index);
        BoundingBox solution = new BoundingBox(-180.0, -90.0, -90.0, 0);

        assertThat(bboxTL, closeTo(solution, 0.00000001));
        assertThat(bboxBL, closeTo(solution, 0.00000001));
        assertThat(bboxTLswap, closeTo(solution, 0.00000001));
        assertThat(bboxBLswap, closeTo(solution, 0.00000001));
    }

    @Test
    public void testBounds() throws Exception {
        BoundingBox bboxTL = gridSetTL.getBounds();
        BoundingBox bboxBL = gridSetBL.getBounds();
        BoundingBox bboxTLswap = gridSetTLswap.getBounds();
        BoundingBox bboxBLswap = gridSetBLswap.getBounds();
        BoundingBox solution = new BoundingBox(-180.0, -90.0, 180, 90);

        assertThat(bboxTL, closeTo(solution, 0.00000001));
        assertThat(bboxBL, closeTo(solution, 0.00000001));
        assertThat(bboxTLswap, closeTo(solution, 0.00000001));
        assertThat(bboxBLswap, closeTo(solution, 0.00000001));
    }

    @Test
    public void testBoundsFromRectangle() throws Exception {
        long[] rect = {0, 0, 0, 0, 0};
        BoundingBox bboxTL = gridSetTL.boundsFromRectangle(rect);
        BoundingBox bboxBL = gridSetBL.boundsFromRectangle(rect);
        BoundingBox bboxTLswap = gridSetTLswap.boundsFromRectangle(rect);
        BoundingBox bboxBLswap = gridSetBLswap.boundsFromRectangle(rect);
        BoundingBox solution = new BoundingBox(-180.0, -90.0, 0.0, 90.0);

        assertThat(bboxTL, equalTo(solution));
        assertThat(bboxBL, equalTo(solution));
        assertThat(bboxTLswap, equalTo(solution));
        assertThat(bboxBLswap, equalTo(solution));

        long[] rect2 = {2, 1, 2, 1, 1};
        BoundingBox bboxTL2 = gridSetTL.boundsFromRectangle(rect2);
        BoundingBox bboxBL2 = gridSetBL.boundsFromRectangle(rect2);
        BoundingBox bboxTLswap2 = gridSetTLswap.boundsFromRectangle(rect2);
        BoundingBox bboxBLswap2 = gridSetBLswap.boundsFromRectangle(rect2);
        BoundingBox solution2 = new BoundingBox(0.0, 0.0, 90.0, 90.0);

        assertThat(bboxTL2, equalTo(solution2));
        assertThat(bboxBL2, equalTo(solution2));
        assertThat(bboxTLswap2, equalTo(solution2));
        assertThat(bboxBLswap2, equalTo(solution2));
    }

    @Test
    public void testClosestIndex() throws Exception {
        BoundingBox box = new BoundingBox(-180.0, -90.0, -90.0, 0);
        long[] idxTL = gridSetTL.closestIndex(box);
        long[] idxBL = gridSetBL.closestIndex(box);
        long[] solution = {0, 0, 1};

        assertArrayEquals(idxTL, solution);
        assertArrayEquals(idxBL, solution);
    }

    @Test
    public void testClosestRectangle() throws Exception {
        BoundingBox box = new BoundingBox(-180.0, -90.0, 0.0, 0.0);
        long[] rectTL = gridSetTL.closestRectangle(box);
        long[] rectBL = gridSetBL.closestRectangle(box);
        long[] solution = {0, 0, 1, 0, 1};

        assertArrayEquals(rectTL, solution);
        assertArrayEquals(rectBL, solution);
    }

    @Test
    public void testGetLeftTopCorner() throws Exception {
        double[] tlTL = gridSetTL.getOrderedTopLeftCorner(1);
        double[] tlBL = gridSetBL.getOrderedTopLeftCorner(1);

        assertThat(tlBL[1], Matchers.closeTo(90.0, 0.01));
        assertThat(tlTL[1], Matchers.closeTo(90.0, 0.01));
    }

    @Test
    public void testClosestIndexInvalidBounds1() throws Exception {
        BoundingBox box = new BoundingBox(0, -180, 180.0, 0);
        assertThrows(GridAlignmentMismatchException.class, () -> gridSetTL.closestIndex(box));
    }

    @Test
    public void testClosestIndexInvalidBounds2() throws Exception {
        BoundingBox box = new BoundingBox(0, 0, 180.0, 180);
        assertThrows(GridAlignmentMismatchException.class, () -> gridSetTL.closestIndex(box));
    }
}
