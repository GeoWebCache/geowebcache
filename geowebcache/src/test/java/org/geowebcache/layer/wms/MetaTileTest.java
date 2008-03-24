package org.geowebcache.layer.wms;

import java.util.Arrays;

import org.geowebcache.layer.wms.GridCalculator;
import org.geowebcache.layer.wms.LayerProfile;
import org.geowebcache.layer.wms.WMSMetaTile;
import org.geowebcache.util.wms.BBOX;

import junit.framework.TestCase;

public class MetaTileTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void test1MetaTile() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0, 0, 180, 90);
        profile.gridBase = new BBOX(-180, -90, 180, 90);
        profile.metaHeight = 1;
        profile.metaWidth = 1;
        double maxTileWidth = 180.0;
        double maxTileHeight = 180.0;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile, profile.bbox, maxTileWidth, maxTileHeight);
        int[] gridPos = { 0, 0, 0 };
        //int[] gridBounds, int[] tileGridPosition, int metaX, int metaY
        WMSMetaTile mt = new WMSMetaTile(gridCalc.getGridBounds(gridPos[2]), gridPos, profile.metaWidth, profile.metaHeight);

        int[] solution = { 0, 0, 0, 0, 0 };
        boolean test = Arrays.equals(mt.getMetaTileGridBounds(), solution);
        if (!test) {
            System.out.println("1 - " + mt.debugString());
            System.out.println("test1MetaTile {" + Arrays.toString(solution)
                    + "} {" + Arrays.toString(mt.getMetaTileGridBounds()) + "}");
        }
        assert (test);
    }

    public void test2MetaTile() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0, 0, 180, 90);
        profile.gridBase = new BBOX(-180, -90, 180, 90);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
        double maxTileWidth = 180.0;
        double maxTileHeight = 180.0;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile, profile.bbox, maxTileWidth, maxTileHeight);
        int[] gridPos = { 127, 63, 6 };
        WMSMetaTile mt = new WMSMetaTile(gridCalc.getGridBounds(gridPos[2]), gridPos, profile.metaWidth, profile.metaHeight);

        int[] solution = { 126, 63, 127, 63, 6 };
        boolean test = Arrays.equals(mt.getMetaTileGridBounds(), solution);
        if (!test) {
            System.out.println("2 - " + mt.debugString());
            System.out.println("test2MetaTile {" + Arrays.toString(solution)
                    + "} {" + Arrays.toString(mt.getMetaTileGridBounds()) + "}");
        }
        assert (test);
    }

    public void test3MetaTile() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0, 0, 20037508.34, 20037508.34);
        profile.gridBase = new BBOX(-20037508.34, -20037508.34, 20037508.34,
                20037508.34);
        profile.metaHeight = 1;
        profile.metaWidth = 1;
        double maxTileWidth = 20037508.34 * 2;
        double maxTileHeight = 20037508.34 * 2;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile, profile.bbox, maxTileWidth, maxTileHeight);
        int[] gridPos = { 0, 0, 0 };
        WMSMetaTile mt = new WMSMetaTile(gridCalc.getGridBounds(gridPos[2]), gridPos, profile.metaWidth, profile.metaHeight);

        int[] solution = { 0, 0, 0, 0, 0 };
        boolean test = Arrays.equals(mt.getMetaTileGridBounds(), solution);
        if (!test) {
            System.out.println("3 - " + mt.debugString());
            System.out.println("test3MetaTile {" + Arrays.toString(solution)
                    + "} {" + Arrays.toString(mt.getMetaTileGridBounds()) + "}");
        }
        assert (test);
    }

    public void test4MetaTile() throws Exception {
        LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0, 0, 20037508.34, 20037508.34);
        profile.gridBase = new BBOX(-20037508.34, -20037508.34, 20037508.34, 20037508.34);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
        double maxTileWidth = 20037508.34 * 2;
        double maxTileHeight = 20037508.34 * 2;
        profile.zoomStart = 0;
        profile.zoomStop = 20;

        GridCalculator gridCalc = new GridCalculator(profile, profile.bbox, maxTileWidth, maxTileHeight);
        int[] gridPos = { 70, 70, 6 };
        WMSMetaTile mt = new WMSMetaTile(gridCalc.getGridBounds(gridPos[2]), gridPos, profile.metaWidth, profile.metaHeight);

        int[] solution = { 69, 69, 63, 63, 6 };
        boolean test = Arrays.equals(mt.getMetaTileGridBounds(), solution);
        if (test) {

        } else {
            System.out.println("4 - " + mt.debugString());
            System.out.println("test4MetaTile {" + Arrays.toString(solution)
                    + "} {" + Arrays.toString(mt.getMetaTileGridBounds()) + "}");
        }
        assert (test);
    }
}