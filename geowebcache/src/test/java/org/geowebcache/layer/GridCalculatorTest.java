package org.geowebcache.layer;

import java.util.Arrays;

import junit.framework.TestCase;

public class GridCalculatorTest extends TestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void test1gridLevels4326() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0, 0, 180, 90);
        profile.gridBase = new BBOX(-180, -90, 180, 90);
        profile.metaHeight = 1;
        profile.metaWidth = 1;
        profile.maxTileWidth = 180.0;
        profile.maxTileHeight = 180.0;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile);

        int[][] solution = { { 1, 0, 1, 0 }, { 2, 1, 3, 1 }, { 4, 2, 7, 3 },
                { 8, 4, 15, 7 }, { 16, 8, 31, 15 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assert (Arrays.equals(solution[i], bounds));
        }
    }

    public void test2gridLevels4326() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0, 0, 180, 90);
        profile.gridBase = new BBOX(-180, -90, 180, 90);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
        profile.maxTileWidth = 180.0;
        profile.maxTileHeight = 180.0;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile);

        int[][] solution = { { 1, 0, 1, 0 }, { 0, 0, 3, 1 }, { 3, 0, 7, 3 },
                { 6, 3, 15, 7 }, { 15, 6, 31, 15 }, { 30, 15, 63, 31 },
                { 63, 30, 127, 63 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assert (Arrays.equals(solution[i], bounds));
        }
    }

    public void test3gridLevels4326() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(-10.0, -10.0, 10.0, 10.0);
        profile.gridBase = new BBOX(-180, -90, 180, 90);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
        profile.maxTileWidth = 180.0;
        profile.maxTileHeight = 180.0;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile);

        int[][] solution = { { 0, 0, 1, 0 }, { 0, 0, 2, 1 }, { 3, 0, 5, 2 },
                { 6, 3, 8, 5 }, { 15, 6, 17, 8 }, { 30, 12, 35, 17 },
                { 60, 27, 68, 35 }, { 120, 54, 137, 71 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assert (Arrays.equals(solution[i], bounds));
        }
    }

    public void test4gridLevels4326() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(175.0, 87.0, 180.0, 90.0);
        profile.gridBase = new BBOX(-180, -90, 180, 90);
        profile.metaHeight = 4;
        profile.metaWidth = 4;
        profile.maxTileWidth = 180.0;
        profile.maxTileHeight = 180.0;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile);

        int[][] solution = { { 1, 0, 1, 0 }, { 3, 1, 3, 1 }, { 4, 0, 7, 3 },
                { 12, 4, 15, 7 }, { 28, 12, 31, 15 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assert (Arrays.equals(solution[i], bounds));
        }
    }

    public void test1gridLevels900913() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0, 0, 20037508.34, 20037508.34);
        profile.gridBase = new BBOX(-20037508.34, -20037508.34, 20037508.34,
                20037508.34);
        profile.metaHeight = 1;
        profile.metaWidth = 1;
        profile.maxTileWidth = 20037508.34 * 2;
        profile.maxTileHeight = 20037508.34 * 2;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile);

        int[][] solution = { { 0, 0, 0, 0 }, { 1, 1, 1, 1 }, { 2, 2, 3, 3 },
                { 4, 4, 7, 7 }, { 8, 8, 15, 15 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println("test1gridLevels900913, level " + i);
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assert (Arrays.equals(solution[i], bounds));
        }
    }

    public void test2gridLevels900913() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0, 0, 20037508.34, 20037508.34);
        profile.gridBase = new BBOX(-20037508.34, -20037508.34, 20037508.34,
                20037508.34);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
        profile.maxTileWidth = 20037508.34 * 2;
        profile.maxTileHeight = 20037508.34 * 2;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile);

        int[][] solution = { { 0, 0, 0, 0 }, { 1, 1, 1, 1 }, { 0, 0, 3, 3 },
                { 3, 3, 7, 7 }, { 6, 6, 15, 15 }, { 15, 15, 31, 31 },
                { 30, 30, 63, 63 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println("test2gridLevels900913, level " + i);
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assert (Arrays.equals(solution[i], bounds));
        }
    }

    public void test3gridLevels900913() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(-500000, -500000, 500000, 500000);
        profile.gridBase = new BBOX(-20037508.34, -20037508.34, 20037508.34,
                20037508.34);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
        profile.maxTileWidth = 20037508.34 * 2;
        profile.maxTileHeight = 20037508.34 * 2;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile);

        int[][] solution = { { 0, 0, 0, 0 }, { 0, 0, 1, 1 }, { 0, 0, 2, 2 },
                { 3, 3, 5, 5 }, { 6, 6, 8, 8 }, { 15, 15, 17, 17 },
                { 30, 30, 32, 32 }, { 60, 60, 65, 65 } };

        for (int i = 0; i < solution.length; i++) {
            int[] bounds = gridCalc.getGridBounds(i);

            if (!Arrays.equals(solution[i], bounds)) {
                System.out.println("test3gridLevels900913, level " + i);
                System.out.println(Arrays.toString(solution[i]) + "  "
                        + Arrays.toString(bounds));
            }
            assert (Arrays.equals(solution[i], bounds));
        }
    }

}