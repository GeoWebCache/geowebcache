package org.geowebcache.grid;

import org.junit.Assert;
import org.junit.Test;

public class GridSetFactoryTest {

    @Test
    public void testResolutionsArrayEPSG4326() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 90);
        double[] resolutions = {180.0 / 256, 180.0 / 512, 180.0 / 1024, 180.0 / 2048};

        GridSet gridSet = GridSetFactory.createGridSet(
                "test", SRS.getEPSG4326(), extent, false, resolutions, null, null, 0.00028, null, 256, 256, false);

        Assert.assertEquals("test", gridSet.getName());
        Assert.assertEquals(-180.0, gridSet.tileOrigin()[0], 0d);
        Assert.assertEquals(-90.0, gridSet.tileOrigin()[1], 0d);
        Assert.assertEquals(resolutions.length, gridSet.getNumLevels());

        Grid grid0 = gridSet.getGrid(0);

        Assert.assertEquals(180.0 / 256, grid0.getResolution(), 0d);
        Assert.assertEquals(2L, grid0.getNumTilesWide());
        Assert.assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGrid(3);

        Assert.assertEquals(180.0 / 2048, grid3.getResolution(), 0d);
        Assert.assertEquals((long) Math.pow(2, 4), grid3.getNumTilesWide());
        Assert.assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }

    @Test
    public void testResolutionsArrayEPSG3785() throws Exception {
        BoundingBox extent = new BoundingBox(-20037508.34, -20037508.34, 20037508.34, 20037508.34);
        double[] resolutions = {
            (20037508.34 * 2) / 256, (20037508.34 * 2) / 512, (20037508.34 * 2) / 1024, (20037508.34 * 2) / 2048
        };

        GridSet gridSet = GridSetFactory.createGridSet(
                "test", SRS.getEPSG3857(), extent, false, resolutions, null, null, 0.00028, null, 256, 256, false);

        Grid grid0 = gridSet.getGrid(0);

        Assert.assertEquals((20037508.34 * 2) / 256, grid0.getResolution(), 0d);
        Assert.assertEquals(1L, grid0.getNumTilesWide());
        Assert.assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGrid(3);

        Assert.assertEquals((long) Math.pow(2, 3), grid3.getNumTilesWide());
        Assert.assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }

    @Test
    public void testResolutionsArrayTricky1() throws Exception {
        // This should be expanded in the X direction
        BoundingBox extent = new BoundingBox(-173, -90, 180, 96);
        double[] resolutions = {180.0 / 200, 180.0 / 400, 180.0 / 800};

        GridSet gridSet = GridSetFactory.createGridSet(
                "test", SRS.getEPSG4326(), extent, false, resolutions, null, null, 0.00028, null, 200, 200, false);

        Assert.assertEquals(-173.0, gridSet.tileOrigin()[0], 0d);
        Assert.assertEquals(-90.0, gridSet.tileOrigin()[1], 0d);
        Assert.assertEquals(resolutions.length, gridSet.getNumLevels());

        Grid grid0 = gridSet.getGrid(0);

        Assert.assertEquals(180.0 / 200, grid0.getResolution(), 0d);
        Assert.assertEquals(2L, grid0.getNumTilesWide());
        Assert.assertEquals(2L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGrid(2);

        Assert.assertEquals(180.0 / 800, grid3.getResolution(), 0d);
        Assert.assertEquals(8L, grid3.getNumTilesWide());
        Assert.assertEquals(5L, grid3.getNumTilesHigh());
    }

    @Test
    public void testBoundingBoxEPSG4326() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 90);

        GridSet gridSet = GridSetFactory.createGridSet(
                "test", SRS.getEPSG4326(), extent, false, 4, null, 0.00028, 256, 256, false);

        Assert.assertEquals("test", gridSet.getName());
        Assert.assertEquals(-180.0, gridSet.tileOrigin()[0], 0d);
        Assert.assertEquals(-90.0, gridSet.tileOrigin()[1], 0d);
        Assert.assertEquals(4, gridSet.getNumLevels());

        Grid grid0 = gridSet.getGrid(0);

        Assert.assertEquals(180.0 / 256, grid0.getResolution(), 0d);
        Assert.assertEquals(2L, grid0.getNumTilesWide());
        Assert.assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGrid(3);

        Assert.assertEquals(180.0 / 2048, grid3.getResolution(), 0d);
        Assert.assertEquals((long) Math.pow(2, 4), grid3.getNumTilesWide());
        Assert.assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }

    @Test
    public void testBoundingBoxEPSG3785() throws Exception {
        BoundingBox extent = new BoundingBox(-20037508.34, -20037508.34, 20037508.34, 20037508.34);
        GridSet gridSet = GridSetFactory.createGridSet(
                "test", SRS.getEPSG3857(), extent, false, 6, null, 0.00028, 256, 256, false);

        Grid grid0 = gridSet.getGrid(0);

        Assert.assertEquals((20037508.34 * 2) / 256, grid0.getResolution(), 0d);
        Assert.assertEquals(1L, grid0.getNumTilesWide());
        Assert.assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGrid(3);

        Assert.assertEquals((long) Math.pow(2, 3), grid3.getNumTilesWide());
        Assert.assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }

    @Test
    public void testBoundingBoxTricky1() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 172, 90);

        GridSet gridSet = GridSetFactory.createGridSet(
                "test", SRS.getEPSG4326(), extent, false, 4, null, 0.00028, 256, 256, false);

        Assert.assertEquals("test", gridSet.getName());
        Assert.assertEquals(-180.0, gridSet.tileOrigin()[0], 0d);
        Assert.assertEquals(-90.0, gridSet.tileOrigin()[1], 0d);
        Assert.assertEquals(4, gridSet.getNumLevels());

        Grid grid0 = gridSet.getGrid(0);

        Assert.assertEquals(180.0 / 256, grid0.getResolution(), 0d);
        Assert.assertEquals(2L, grid0.getNumTilesWide());
        Assert.assertEquals(1L, grid0.getNumTilesHigh());
    }

    @Test
    public void testBoundingBoxTricky2() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 82);

        SRS srs = SRS.getEPSG4326();

        GridSet gridSet = GridSetFactory.createGridSet("test", srs, extent, false, 4, null, 0.00028, 256, 256, false);

        Assert.assertEquals("test", gridSet.getName());
        Assert.assertEquals(-180.0, gridSet.tileOrigin()[0], 0d);
        Assert.assertEquals(-90.0, gridSet.tileOrigin()[1], 0d);
        Assert.assertEquals(4, gridSet.getNumLevels());

        Grid grid0 = gridSet.getGrid(0);

        Assert.assertEquals(180.0 / 256, grid0.getResolution(), 0d);
        Assert.assertEquals(2L, grid0.getNumTilesWide());
        Assert.assertEquals(1L, grid0.getNumTilesHigh());
    }

    @Test
    public void testResolutionsPreservedFlag() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 90);
        double[] resolutions = {180.0 / 256, 180.0 / 512, 180.0 / 1024, 180.0 / 2048};
        double[] scales = {500E6, 250E6, 100E6};

        GridSet gridSet = GridSetFactory.createGridSet(
                "test", SRS.getEPSG4326(), extent, false, resolutions, null, null, 0.00028, null, 256, 256, false);

        Assert.assertTrue(gridSet.isResolutionsPreserved());

        gridSet = GridSetFactory.createGridSet(
                "test", SRS.getEPSG4326(), extent, false, null, scales, null, 0.00028, null, 256, 256, false);

        Assert.assertFalse(gridSet.isResolutionsPreserved());
    }

    @Test
    public void testLevels2() throws Exception {
        BoundingBox extent = new BoundingBox(0, 0, 1000, 1000);
        int levels = 16;
        int tileW = 300, tileH = 100;
        Double metersPerUnit = 1D;
        double pixelSize = GridSetFactory.DEFAULT_PIXEL_SIZE_METER;

        GridSet gridSet = GridSetFactory.createGridSet(
                "test", SRS.getSRS(3005), extent, false, levels, metersPerUnit, pixelSize, tileW, tileH, false);

        Assert.assertEquals(extent, gridSet.getOriginalExtent());
        Assert.assertEquals(16, gridSet.getNumLevels());
        Assert.assertEquals(1, gridSet.getGrid(0).getNumTilesWide());
        Assert.assertEquals(3, gridSet.getGrid(0).getNumTilesHigh());

        for (int i = 1; i < gridSet.getNumLevels(); i++) {
            Assert.assertEquals(
                    2 * gridSet.getGrid(i - 1).getNumTilesWide(),
                    gridSet.getGrid(i).getNumTilesWide());
            Assert.assertEquals(
                    2 * gridSet.getGrid(i - 1).getNumTilesHigh(),
                    gridSet.getGrid(i).getNumTilesHigh());
        }
    }

    @Test
    public void testWideBoundsTallTile() throws Exception {
        BoundingBox extent = new BoundingBox(0, 0, 100, 45);

        // should give 4x1 tiles, with bounds height expanded to 50
        int tileWidth = 10;
        int tileHeight = 20;

        SRS srs = SRS.getEPSG4326();

        boolean alignTopLeft = false;
        GridSet gridSet = GridSetFactory.createGridSet(
                "test", srs, extent, alignTopLeft, 4, null, 0.00028, tileWidth, tileHeight, false);

        Assert.assertEquals("test", gridSet.getName());
        Assert.assertEquals(0D, gridSet.tileOrigin()[0], 0d);
        Assert.assertEquals(0D, gridSet.tileOrigin()[1], 0d);

        Assert.assertEquals(new BoundingBox(0, 0, 100, 50), gridSet.getBounds());

        Assert.assertEquals(4, gridSet.getNumLevels());

        Grid grid0 = gridSet.getGrid(0);

        Assert.assertEquals(4L, grid0.getNumTilesWide());
        Assert.assertEquals(1L, grid0.getNumTilesHigh());
        Assert.assertEquals(50D / 20D, grid0.getResolution(), 0d);

        alignTopLeft = true;
        gridSet = GridSetFactory.createGridSet(
                "test", srs, extent, alignTopLeft, 4, null, 0.00028, tileWidth, tileHeight, false);

        Assert.assertEquals(new BoundingBox(0, -5, 100, 45), gridSet.getBounds());
        Assert.assertEquals("test", gridSet.getName());
        Assert.assertEquals(0D, gridSet.tileOrigin()[0], 0d);
        Assert.assertEquals(45D, gridSet.tileOrigin()[1], 0d);
    }

    @Test
    public void testTallBoundsWideTile() throws Exception {
        BoundingBox extent = new BoundingBox(0, 0, 100, 490);

        // should give 1x10 tiles, with bounds width expanded to 500
        int tileWidth = 20;
        int tileHeight = 10;

        SRS srs = SRS.getEPSG4326();

        boolean alignTopLeft = false;
        GridSet gridSet = GridSetFactory.createGridSet(
                "test", srs, extent, alignTopLeft, 4, null, 0.00028, tileWidth, tileHeight, false);

        Assert.assertEquals(new BoundingBox(0, 0, 100, 500), gridSet.getBounds());

        Assert.assertEquals(4, gridSet.getNumLevels());

        Grid grid0 = gridSet.getGrid(0);

        long tilesWide = 1;
        long tilesHigh = 10;

        Assert.assertEquals(tilesWide, grid0.getNumTilesWide());
        Assert.assertEquals(tilesHigh, grid0.getNumTilesHigh());
        Assert.assertEquals(500D / tileHeight / tilesHigh, grid0.getResolution(), 0d);

        alignTopLeft = true;
        gridSet = GridSetFactory.createGridSet(
                "test", srs, extent, alignTopLeft, 4, null, 0.00028, tileWidth, tileHeight, false);

        BoundingBox bounds = gridSet.getBounds();
        Assert.assertEquals(new BoundingBox(0, -10, 100, 490), bounds);

        Assert.assertEquals("test", gridSet.getName());
        Assert.assertEquals(0D, gridSet.tileOrigin()[0], 0d);
        Assert.assertEquals(490D, gridSet.tileOrigin()[1], 0d);
    }
}
