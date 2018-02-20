package org.geowebcache.grid;

import java.util.Arrays;

import junit.framework.TestCase;

/**
 * The GridCalculator is gone,
 * 
 * but its legacy lives on through the tests we still have to pass...
 */
public class GridCalculatorTest extends TestCase {

    GridSetBroker gridSetBroker = new GridSetBroker(false, false);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void test1gridLevels4326() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180.0, 90.0);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0,
                10);

        long[][] solution = { { 1, 0, 1, 0, 0 }, { 2, 1, 3, 1, 1 }, { 4, 2, 7, 3, 2 },
                { 8, 4, 15, 7, 3 } };

        for (int i = 0; i < solution.length; i++) {
            long[] bounds = grid.getCoverage(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(i + " " + Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test2gridLevels4326() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0,
                10);

        long[][] solution = { { 1, 0, 1, 0, 0 }, { 2, 1, 3, 1, 1 }, { 4, 2, 7, 3, 2 },
                { 8, 4, 15, 7, 3 }, { 16, 8, 31, 15, 4 }, { 32, 16, 63, 31, 5 },
                { 64, 32, 127, 63, 6 } };

        for (int i = 0; i < solution.length; i++) {
            long[] bounds = grid.getCoverage(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  " + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test3gridLevels4326() throws Exception {
        BoundingBox bbox = new BoundingBox(-10.0, -10.0, 10.0, 10.0);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0,
                10);

        long[][] solution = { { 0, 0, 1, 0, 0 }, { 1, 0, 2, 1, 1 }, { 3, 1, 4, 2, 2 },
                { 7, 3, 8, 4, 3 }, { 15, 7, 16, 8, 4 }, { 30, 14, 33, 17, 5 },
                { 60, 28, 67, 35, 6 }, { 120, 56, 135, 71, 7 } };

        for (int i = 0; i < solution.length; i++) {
            long[] bounds = grid.getCoverage(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  " + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test4gridLevels4326() throws Exception {
        BoundingBox bbox = new BoundingBox(175.0, 87.0, 180.0, 90.0);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0,
                10);

        long[][] solution = { { 1, 0, 1, 0, 0 }, { 3, 1, 3, 1, 1 }, { 7, 3, 7, 3, 2 },
                { 15, 7, 15, 7, 3 }, { 31, 15, 31, 15, 4 }, { 63, 31, 63, 31, 5 },
                { 126, 62, 127, 63, 6 }, { 252, 125, 255, 127, 7 }, { 504, 251, 511, 255, 8 } };

        for (int i = 0; i < solution.length; i++) {
            long[] bounds = grid.getCoverage(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  " + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test1gridLevels900913() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 20037508.34, 20037508.34);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg3857(), bbox, 0,
                10);

        long[][] solution = { { 0, 0, 0, 0, 0 }, { 1, 1, 1, 1, 1 }, { 2, 2, 3, 3, 2 },
                { 4, 4, 7, 7, 3 }, { 8, 8, 15, 15, 4 } };

        for (int i = 0; i < solution.length; i++) {
            long[] bounds = grid.getCoverage(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println("test1gridLevels900913, level " + i);
                System.out.println(Arrays.toString(solution[i]) + "  " + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test2gridLevels900913() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 20037508.34, 20037508.34);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg3857(), bbox, 0,
                10);

        long[][] solution = { { 0, 0, 0, 0, 0 }, { 1, 1, 1, 1, 1 }, { 2, 2, 3, 3, 2 },
                { 4, 4, 7, 7, 3 }, { 8, 8, 15, 15, 4 }, { 16, 16, 31, 31, 5 },
                { 32, 32, 63, 63, 6 } };

        for (int i = 0; i < solution.length; i++) {
            long[] bounds = grid.getCoverage(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println("test2gridLevels900913, level " + i);
                System.out.println(Arrays.toString(solution[i]) + "  " + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test3gridLevels900913() throws Exception {
        BoundingBox bbox = new BoundingBox(-500000, -500000, 500000, 500000);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg3857(), bbox, 0,
                10);

        long[][] solution = { { 0, 0, 0, 0, 0 }, { 0, 0, 1, 1, 1 }, { 1, 1, 2, 2, 2 },
                { 3, 3, 4, 4, 3 }, { 7, 7, 8, 8, 4 }, { 15, 15, 16, 16, 5 }, { 31, 31, 32, 32, 6 },
                { 62, 62, 65, 65, 7 } };

        for (int i = 0; i < solution.length; i++) {
            long[] bounds = grid.getCoverage(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println("test3gridLevels900913, level " + i);
                System.out.println(Arrays.toString(solution[i]) + "  " + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test5gridBoundsLoc4326() throws Exception {
        BoundingBox bbox = new BoundingBox(-124.73, 24.96, -66.97, 49.37);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0,
                10);

        long[] bestFit = grid.getCoverageBestFit();
        long[] solution = { 0, 0, 0, 0, 0 };
        assertTrue(Arrays.equals(bestFit, solution));
    }

    public void test6gridLoctoBounds4326() throws Exception {
        BoundingBox bbox = new BoundingBox(-124.73, 24.96, -66.97, 49.37);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0,
                10);

        long[] gridLoc1 = { 1, 1, 1 };
        BoundingBox box1 = grid.boundsFromIndex(gridLoc1);

        boolean box1_comparison = box1.equals(new BoundingBox(-90.0, 0.0, 0.0, 90.0));
        assertTrue(box1_comparison);
        boolean box1_kml = box1.toKMLLatLonBox().equals(
                "<LatLonBox><north>90.0</north><south>0.0</south>"
                        + "<east>0.0</east><west>-90.0</west></LatLonBox>");
        assertTrue(box1_kml);

        long[] gridLoc2 = { 5, 1, 2 };
        BoundingBox box2 = grid.boundsFromIndex(gridLoc2);
        boolean box2_comparison = box2.equals(new BoundingBox(45.0, -45.0, 90.0, 0.0));
        assertTrue(box2_comparison);
        boolean box2_kml = box2.toKMLLatLonAltBox().equals(
                "<LatLonAltBox><north>0.0</north><south>-45.0</south>"
                        + "<east>90.0</east><west>45.0</west></LatLonAltBox>");
        assertTrue(box2_kml);
    }

    public void test5gridLevels4326() throws Exception {
        BoundingBox bbox = new BoundingBox(-124.731422, 24.955967, -66.969849, 49.371735);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0,
                10);

        long[][] solution = { { 0, 0, 0, 0, 0 }, { 0, 1, 1, 1, 1 }, { 1, 2, 2, 3, 2 },
                { 2, 5, 5, 6, 3 } };

        for (int i = 0; i < solution.length; i++) {
            long[] bounds = grid.getCoverage(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  " + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test0linearSearch() throws Exception {
        BoundingBox bbox = new BoundingBox(-4.0, -4.0, 4.0, 4.0);
        double[] resolutions = { 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0 };

        GridSet gridSet = GridSetFactory.createGridSet("bogus", SRS.getSRS(0), bbox, false,
                resolutions, null, null, 0.00028, null, 256, 256, false);
        GridSubset gridSubset = GridSubsetFactory.createGridSubSet(gridSet);

        BoundingBox tileBounds = createApproximateTileBounds(gridSubset, bbox, 5.04, 256, 256);
        long[] test = gridSubset.closestIndex(tileBounds);
        assertEquals(3L, test[2]);

        tileBounds = createApproximateTileBounds(gridSubset, bbox, 8.03, 256, 256);
        test = gridSubset.closestIndex(tileBounds);
        assertEquals(0L, test[2]);

        tileBounds = createApproximateTileBounds(gridSubset, bbox, 0.98, 256, 256);
        test = gridSubset.closestIndex(tileBounds);
        assertEquals(7L, test[2]);

        tileBounds = createApproximateTileBounds(gridSubset, bbox, 1.005, 256, 256);
        test = gridSubset.closestIndex(tileBounds);
        assertEquals(7L, test[2]);

        tileBounds = createApproximateTileBounds(gridSubset, bbox, 6.025, 256, 256);
        test = gridSubset.closestIndex(tileBounds);
        assertEquals(2L, test[2]);
    }

    public void testCustomSRSGridBottomLeft() throws Exception {
        // This mimics the Spearfish layer
        BoundingBox bbox = new BoundingBox(587334.20625, 4912451.9275, 611635.54375,
                4936753.265000001);
        BoundingBox gridBase = new BoundingBox(587334.20625, 4912451.9275, 611635.54375,
                4936753.265000001);

        GridSet gridSet = GridSetFactory.createGridSet("bogus", SRS.getSRS(26713), gridBase, false,
                30, null, 0.00028, 256, 256, false);
        GridSubset gridSubset = GridSubsetFactory.createGridSubSet(gridSet, bbox, 0, 20);

        // Test the basic algorithm for calculating appropriate resolutions
        assertTrue(Math.abs(gridSubset.getResolutions()[0] - 94.9270) / 94.9270 < 0.01);

        // Check the actual max bounds
        long[] solution = { 0, 0, 0 };
        assertTrue(Arrays.equals(solution, gridSubset.closestIndex(bbox)));

        // Test a grid location
        long[] gridLoc = { 1, 0, 1 };
        BoundingBox bboxSolution = new BoundingBox(599484.8750000002, 4912451.9275,
                611635.5437500004, 4924602.59625);
        assertTrue(bboxSolution.equals(gridSubset.boundsFromIndex(gridLoc)));

        // Now lets go the other way
        assertTrue(Arrays.equals(gridLoc, gridSubset.closestIndex(bboxSolution)));
    }

    public void testTopLeftNaive() throws Exception {
        // This mimics the Spearfish layer
        BoundingBox bbox = new BoundingBox(-180.0, -90.0, 180.0, 90.0);
        BoundingBox gridBase = new BoundingBox(-180.0, -90.0, 180.0, 90.0);

        GridSet gridSet = GridSetFactory.createGridSet("bogus", SRS.getSRS(4326), gridBase, true,
                30, null, 0.00028, 256, 256, false);
        GridSubset gridSubset = GridSubsetFactory.createGridSubSet(gridSet, bbox, 0, 20);

        // Check the actual max bounds
        long[] solution = { 0, 0, 0 };
        long[] closest = gridSubset.closestIndex(new BoundingBox(-180.0, -90.0, 0.0, 90.0));
        assertTrue(Arrays.equals(solution, closest));

        long[] solution2 = { 1, 0, 0 };
        closest = gridSubset.closestIndex(new BoundingBox(0.0, -90.0, 180.0, 90.0));
        assertTrue(Arrays.equals(solution2, closest));

        long[] t1 = { 0, 0, 1 }; // 90x90 degrees
        BoundingBox test1 = gridSubset.boundsFromIndex(t1);
        assertTrue(Math.abs(test1.getMinX() + 180.0) < 0.01);
        assertTrue(Math.abs(test1.getMinY() + 90.0) < 0.01);
        assertTrue(Math.abs(test1.getMaxY()) < 0.01);
    }

    public void testCustomSRSGridTopLeft() throws Exception {
        // This mimics the Spearfish layer
        BoundingBox bbox = new BoundingBox(587334.20625, 4912451.9275, 611635.54375,
                4936753.265000001);
        BoundingBox gridBase = new BoundingBox(587334.20625, 4912451.9275, 611635.54375,
                4936753.265000001);

        GridSet gridSet = GridSetFactory.createGridSet("bogus", SRS.getSRS(26713), gridBase, true,
                30, null, 0.00028, 256, 256, false);
        GridSubset gridSubset = GridSubsetFactory.createGridSubSet(gridSet, bbox, 0, 20);

        // Test the basic algorithm for calculating appropriate resolutions
        assertTrue(Math.abs(gridSubset.getResolutions()[0] - 94.9270) / 94.9270 < 0.01);

        // Check the actual max bounds
        long[] solution = { 0, 0, 0 };
        long[] closest = gridSubset.closestIndex(bbox);
        assertTrue(Arrays.equals(solution, closest));

        // Test a grid location
        long[] gridLoc = { 1, 0, 1 };
        BoundingBox bboxSolution = new BoundingBox(599484.8750000002, 4912451.9275,
                611635.5437500004, 4924602.59625);
        assertTrue(bboxSolution.equals(gridSubset.boundsFromIndex(gridLoc)));

        // Now lets go the other way
        closest = gridSubset.closestIndex(bboxSolution);
        assertTrue(Arrays.equals(gridLoc, closest));
    }

    private BoundingBox createApproximateTileBounds(GridSubset gridSubset, BoundingBox bbox,
            double resolution, int tileWidth, int tileHeight) {

        double width = tileWidth * resolution;
        double height = tileHeight * resolution;

        BoundingBox ret = new BoundingBox(bbox.getMinX() + width, bbox.getMinY() + height,
                bbox.getMinX() + width * 2, bbox.getMinY() + height * 2);

        return ret;
    }
}