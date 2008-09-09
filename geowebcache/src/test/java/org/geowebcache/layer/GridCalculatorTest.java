package org.geowebcache.layer;

import java.util.Arrays;

import junit.framework.TestCase;

import org.geowebcache.util.wms.BBOX;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.GridCalculator;
import org.geowebcache.layer.SRS;

public class GridCalculatorTest extends TestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void test1gridLevels4326() throws Exception {
        BBOX bbox = new BBOX(0, 0, 180, 90);
        BBOX gridBase = new BBOX(-180, -90, 180, 90);
        
        Grid grid = new Grid(SRS.getEPSG4326(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();

        int[][] solution = { { 1, 0, 1, 0 }, { 2, 1, 3, 1 }, { 4, 2, 7, 3 },
                { 8, 4, 15, 7 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test2gridLevels4326() throws Exception {
        BBOX bbox = new BBOX(0, 0, 180, 90);
        BBOX gridBase = new BBOX(-180, -90, 180, 90);
        
        Grid grid = new Grid(SRS.getEPSG4326(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();
        
        int[][] solution = { { 1, 0, 1, 0 }, { 2, 1, 3, 1 }, { 4, 2, 7, 3 },
                { 8, 4, 15, 7 }, { 16, 8, 31, 15 }, { 32, 16, 63, 31 },
                { 64, 32, 127, 63 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test3gridLevels4326() throws Exception {
        BBOX bbox = new BBOX(-10.0, -10.0, 10.0, 10.0);
        BBOX gridBase = new BBOX(-180, -90, 180, 90);

        Grid grid = new Grid(SRS.getEPSG4326(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();

        int[][] solution = { { 0, 0, 1, 0 }, { 1, 0, 2, 1 }, { 3, 1, 4, 2 },
                { 7, 3, 8, 4 }, { 15, 7, 16, 8 }, { 30, 14, 33, 17 },
                { 60, 28, 67, 35 }, { 120, 56, 135, 71 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test4gridLevels4326() throws Exception {
        BBOX bbox = new BBOX(175.0, 87.0, 180.0, 90.0);
        BBOX gridBase = new BBOX(-180, -90, 180, 90);
        
        Grid grid = new Grid(SRS.getEPSG4326(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();

        int[][] solution = { { 1, 0, 1, 0 }, { 3, 1, 3, 1 }, { 7, 3, 7, 3 },
                { 15, 7, 15, 7 }, { 31, 15, 31, 15 }, {63, 31, 63, 31}, {126, 62, 127, 63},
                { 252, 125, 255, 127}, {504, 251, 511, 255} };
        
        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test1gridLevels900913() throws Exception {
        BBOX bbox = new BBOX(0, 0, 20037508.34, 20037508.34);
        BBOX gridBase = new BBOX(
        		-20037508.34, -20037508.34, 
        		20037508.34, 20037508.34);
        
        Grid grid = new Grid(SRS.getEPSG4326(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();
        
        int[][] solution = { { 0, 0, 0, 0 }, { 1, 1, 1, 1 }, { 2, 2, 3, 3 },
                { 4, 4, 7, 7 }, { 8, 8, 15, 15 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println("test1gridLevels900913, level " + i);
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test2gridLevels900913() throws Exception {
        BBOX bbox = new BBOX(0, 0, 20037508.34, 20037508.34);
        BBOX gridBase = new BBOX(
        		-20037508.34, -20037508.34, 
        		20037508.34, 20037508.34);
        
        Grid grid = new Grid(SRS.getEPSG900913(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();
        
        int[][] solution = { { 0, 0, 0, 0 }, { 1, 1, 1, 1 }, { 2, 2, 3, 3 },
                { 4, 4, 7, 7 }, { 8, 8, 15, 15 }, { 16, 16, 31, 31 },
                { 32, 32, 63, 63 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println("test2gridLevels900913, level " + i);
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test3gridLevels900913() throws Exception {
        BBOX bbox = new BBOX(-500000, -500000, 500000, 500000);
        BBOX gridBase = new BBOX(
        		-20037508.34, -20037508.34, 
        		20037508.34, 20037508.34);
        
        Grid grid = new Grid(SRS.getEPSG900913(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();
        
        int[][] solution = { { 0, 0, 0, 0 }, { 0, 0, 1, 1 }, { 1, 1, 2, 2 },
                { 3, 3, 4, 4 }, { 7, 7, 8, 8 }, { 15, 15, 16, 16 },
                { 31, 31, 32, 32 }, { 62, 62, 65, 65 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println("test3gridLevels900913, level " + i);
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }

    public void test5gridBoundsLoc4326() throws Exception {
        BBOX bbox = new BBOX(-124.73, 24.96, -66.97, 49.37);
        BBOX gridBase = new BBOX(-180, -90, 180, 90);

        Grid grid = new Grid(SRS.getEPSG4326(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();
        
        int[] gridLoc = gridCalc.getZoomedOutGridLoc();
        int[] solution = {0, 0, 0};
        assertTrue(Arrays.equals(gridLoc, solution));
    }
    
    public void test6gridLoctoBounds4326() throws Exception {
        BBOX bbox = new BBOX(-124.73, 24.96, -66.97, 49.37);
        BBOX gridBase = new BBOX(-180, -90, 180, 90);
        
        Grid grid = new Grid(SRS.getEPSG4326(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();
        
        int[] gridLoc1 = {1, 1, 1};
        BBOX box1 = gridCalc.bboxFromGridLocation(gridLoc1);
        boolean box1_comparison = box1.equals(new BBOX(-90.0,0.0,0.0,90.0));
        assertTrue(box1_comparison);
        boolean box1_kml = box1.toKML().equals(
        		"<LatLonAltBox><north>90.0</north><south>0.0</south>"
        		+"<east>0.0</east><west>-90.0</west></LatLonAltBox>");
        assertTrue(box1_kml);
        
        int[] gridLoc2 = {5, 1, 2};        
        BBOX box2 = gridCalc.bboxFromGridLocation(gridLoc2);
        boolean box2_comparison = box2.equals(new BBOX(45.0,-45.0,90.0,0.0));
        assertTrue(box2_comparison);
        boolean box2_kml = box2.toKML().equals(
        		"<LatLonAltBox><north>0.0</north><south>-45.0</south>"
        		+"<east>90.0</east><west>45.0</west></LatLonAltBox>");
        assertTrue(box2_kml);
    }
    
    public void test5gridLevels4326() throws Exception {
        BBOX bbox = new BBOX(-124.731422, 24.955967, -66.969849, 49.371735);
        BBOX gridBase = new BBOX(-180, -90, 180, 90);
        
        Grid grid = new Grid(SRS.getEPSG4326(), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();
        
        int[][] solution = { { 0, 0, 0, 0 }, { 0, 1, 1, 1 }, { 1, 2, 2, 3 },
                { 2, 5, 5, 6 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assertTrue(Arrays.equals(solution[i], bounds));
        }
    }
    
    public void test0binarySearch() throws Exception {
        double[] resolutions = {8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0};
        int result = GridCalculator.binarySearchForResolution(resolutions, 5.04);
        assertEquals(3, result);
        
        //Culprit
        result = GridCalculator.binarySearchForResolution(resolutions, 8.03);
        assertEquals(0, result);
        
        result = GridCalculator.binarySearchForResolution(resolutions, 0.98);
        assertEquals(7, result);
        
        result = GridCalculator.binarySearchForResolution(resolutions, 1.005);
        assertEquals(7, result);

        result = GridCalculator.binarySearchForResolution(resolutions, 6.025);
        assertEquals(2, result);
    }
    
    public void test1binarySearch() throws Exception {
        double[] resolutions = {12.0, 10.0, 6.0, 5.0, 4.0, 3.0, 1.0};
        int result = GridCalculator.binarySearchForResolution(resolutions, 5.04);
        assertEquals(3, result);
        
        result = GridCalculator.binarySearchForResolution(resolutions, 0.98);
        assertEquals(6, result);
        
        result = GridCalculator.binarySearchForResolution(resolutions, 12.05);
        assertEquals(0, result);
        
        result = GridCalculator.binarySearchForResolution(resolutions, 4.002);
        assertEquals(4, result);
        
    }
    
    public void testCustomSRSGrid() throws Exception {
        // This mimics the Spearfish layer
        BBOX bbox = new BBOX(587334.20625,4912451.9275, 611635.54375,4936753.265000001);
        BBOX gridBase = new BBOX(587334.20625,4912451.9275, 611635.54375,4936753.265000001);
        
        // Test the basic algorithm for calculating appropriate resolutions
        Grid grid = new Grid(SRS.getSRS(26713), bbox, gridBase, null);
        GridCalculator gridCalc = grid.getGridCalculator();
        assertTrue(Math.abs(gridCalc.getResolutions()[0] - 94.9270 ) / 94.9270 < 0.01);
        
        // Check the actual max bounds
        int[] solution = {0,0,0};
        assertTrue(Arrays.equals(solution,gridCalc.gridLocation(bbox)));
        
        // Test a grid location
        int[] gridLoc =  {1, 0, 1};
        BBOX bboxSolution = new BBOX(599484.8750000002,4912451.9275,611635.5437500004,4924602.59625);
        assertTrue(bboxSolution.equals(gridCalc.bboxFromGridLocation(gridLoc)));
        
        // Now lets go the other way
        assertTrue(Arrays.equals(gridCalc.gridLocation(bboxSolution),gridLoc));
        
        // This is a bit easy, but whatever
        int[] zoomedOut = gridCalc.getZoomedOutGridLoc();
        assertTrue(Arrays.equals(solution,zoomedOut));
    }
    
   
    
}