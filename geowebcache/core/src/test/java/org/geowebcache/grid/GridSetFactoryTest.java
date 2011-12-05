package org.geowebcache.grid;

import junit.framework.TestCase;

public class GridSetFactoryTest extends TestCase {

    public void testResolutionsArrayEPSG4326() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 90);
        double[] resolutions = { 180.0 / 256, 180.0 / 512, 180.0 / 1024, 180.0 / 2048 };

        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false,
                resolutions, null, null, 0.00028, null, 256, 256, false);

        assertEquals("test", gridSet.getName());
        assertEquals(-180.0, gridSet.tileOrigin()[0]);
        assertEquals(-90.0, gridSet.tileOrigin()[1]);
        assertEquals(resolutions.length, gridSet.getGridLevels().length);

        Grid grid0 = gridSet.getGridLevels()[0];

        assertEquals(180.0 / 256, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGridLevels()[3];

        assertEquals(180.0 / 2048, grid3.getResolution());
        assertEquals((long) Math.pow(2, 4), grid3.getNumTilesWide());
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }

    public void testResolutionsArrayEPSG3785() throws Exception {
        BoundingBox extent = new BoundingBox(-20037508.34, -20037508.34, 20037508.34, 20037508.34);
        double[] resolutions = { (20037508.34 * 2) / 256, (20037508.34 * 2) / 512,
                (20037508.34 * 2) / 1024, (20037508.34 * 2) / 2048 };

        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG3857(), extent, false,
                resolutions, null, null, 0.00028, null, 256, 256, false);

        Grid grid0 = gridSet.getGridLevels()[0];

        assertEquals((20037508.34 * 2) / 256, grid0.getResolution());
        assertEquals(1L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGridLevels()[3];

        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesWide());
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }

    public void testResolutionsArrayTricky1() throws Exception {
        // This should be expanded in the X direction
        BoundingBox extent = new BoundingBox(-173, -90, 180, 96);
        double[] resolutions = { 180.0 / 200, 180.0 / 400, 180.0 / 800 };

        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false,
                resolutions, null, null, 0.00028, null, 200, 200, false);

        assertEquals(-173.0, gridSet.tileOrigin()[0]);
        assertEquals(-90.0, gridSet.tileOrigin()[1]);
        assertEquals(resolutions.length, gridSet.getGridLevels().length);

        Grid grid0 = gridSet.getGridLevels()[0];

        assertEquals(180.0 / 200, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(2L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGridLevels()[2];

        assertEquals(180.0 / 800, grid3.getResolution());
        assertEquals(8L, grid3.getNumTilesWide());
        assertEquals(5L, grid3.getNumTilesHigh());

    }

    public void testBoundingBoxEPSG4326() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 90);

        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false, 4,
                null, 0.00028, 256, 256, false);

        assertEquals("test", gridSet.getName());
        assertEquals(-180.0, gridSet.tileOrigin()[0]);
        assertEquals(-90.0, gridSet.tileOrigin()[1]);
        assertEquals(4, gridSet.getGridLevels().length);

        Grid grid0 = gridSet.getGridLevels()[0];

        assertEquals(180.0 / 256, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGridLevels()[3];

        assertEquals(180.0 / 2048, grid3.getResolution());
        assertEquals((long) Math.pow(2, 4), grid3.getNumTilesWide());
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }

    public void testBoundingBoxEPSG3785() throws Exception {
        BoundingBox extent = new BoundingBox(-20037508.34, -20037508.34, 20037508.34, 20037508.34);
        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG3857(), extent, false, 6,
                null, 0.00028, 256, 256, false);

        Grid grid0 = gridSet.getGridLevels()[0];

        assertEquals((20037508.34 * 2) / 256, grid0.getResolution());
        assertEquals(1L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());

        Grid grid3 = gridSet.getGridLevels()[3];

        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesWide());
        assertEquals((long) Math.pow(2, 3), grid3.getNumTilesHigh());
    }

    public void testBoundingBoxTricky1() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 172, 90);

        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false, 4,
                null, 0.00028, 256, 256, false);

        assertEquals("test", gridSet.getName());
        assertEquals(-180.0, gridSet.tileOrigin()[0]);
        assertEquals(-90.0, gridSet.tileOrigin()[1]);
        assertEquals(4, gridSet.getGridLevels().length);

        Grid grid0 = gridSet.getGridLevels()[0];

        assertEquals(180.0 / 256, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());
    }

    public void testBoundingBoxTricky2() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 82);

        SRS srs = SRS.getEPSG4326();

        GridSet gridSet = GridSetFactory.createGridSet("test", srs, extent, false, 4, null,
                0.00028, 256, 256, false);

        assertEquals("test", gridSet.getName());
        assertEquals(-180.0, gridSet.tileOrigin()[0]);
        assertEquals(-90.0, gridSet.tileOrigin()[1]);
        assertEquals(4, gridSet.getGridLevels().length);

        Grid grid0 = gridSet.getGridLevels()[0];

        assertEquals(180.0 / 256, grid0.getResolution());
        assertEquals(2L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());
    }

    public void testResolutionsPreservedFlag() throws Exception {
        BoundingBox extent = new BoundingBox(-180, -90, 180, 90);
        double[] resolutions = { 180.0 / 256, 180.0 / 512, 180.0 / 1024, 180.0 / 2048 };
        double[] scales = { 500E6, 250E6, 100E6 };

        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false,
                resolutions, null, null, 0.00028, null, 256, 256, false);

        assertTrue(gridSet.isResolutionsPreserved());

        gridSet = GridSetFactory.createGridSet("test", SRS.getEPSG4326(), extent, false, null,
                scales, null, 0.00028, null, 256, 256, false);

        assertFalse(gridSet.isResolutionsPreserved());
    }

    public void testLevels2() throws Exception {
        BoundingBox extent = new BoundingBox(0, 0, 1000, 1000);
        int levels = 16;
        int tileW = 300, tileH = 100;
        Double metersPerUnit = 1D;
        double pixelSize = GridSetFactory.DEFAULT_PIXEL_SIZE_METER;

        GridSet gridSet = GridSetFactory.createGridSet("test", SRS.getSRS(3005), extent, false,
                levels, metersPerUnit, pixelSize, tileW, tileH, false);

        assertEquals(extent, gridSet.getOriginalExtent());
        Grid[] gridLevels = gridSet.getGridLevels();
        assertEquals(16, gridLevels.length);
        assertEquals(1, gridLevels[0].getNumTilesWide());
        assertEquals(3, gridLevels[0].getNumTilesHigh());

        for (int i = 1; i < gridLevels.length; i++) {
            assertEquals(2 * gridLevels[i - 1].getNumTilesWide(), gridLevels[i].getNumTilesWide());
            assertEquals(2 * gridLevels[i - 1].getNumTilesHigh(), gridLevels[i].getNumTilesHigh());
        }
    }

    public void testWideBoundsTallTile() throws Exception {
        BoundingBox extent = new BoundingBox(0, 0, 100, 45);

        // should give 4x1 tiles, with bounds height expanded to 50
        int tileWidth = 10;
        int tileHeight = 20;

        SRS srs = SRS.getEPSG4326();

        boolean alignTopLeft = false;
        GridSet gridSet = GridSetFactory.createGridSet("test", srs, extent, alignTopLeft, 4, null,
                0.00028, tileWidth, tileHeight, false);

        assertEquals("test", gridSet.getName());
        assertEquals(0D, gridSet.tileOrigin()[0]);
        assertEquals(0D, gridSet.tileOrigin()[1]);

        assertEquals(new BoundingBox(0, 0, 100, 50), gridSet.getBounds());

        assertEquals(4, gridSet.getGridLevels().length);

        Grid grid0 = gridSet.getGridLevels()[0];

        assertEquals(4L, grid0.getNumTilesWide());
        assertEquals(1L, grid0.getNumTilesHigh());
        assertEquals(50D / 20D, grid0.getResolution());

        alignTopLeft = true;
        gridSet = GridSetFactory.createGridSet("test", srs, extent, alignTopLeft, 4, null, 0.00028,
                tileWidth, tileHeight, false);

        assertEquals(new BoundingBox(0, -5, 100, 45), gridSet.getBounds());
        assertEquals("test", gridSet.getName());
        assertEquals(0D, gridSet.tileOrigin()[0]);
        assertEquals(45D, gridSet.tileOrigin()[1]);
    }

    public void testTallBoundsWideTile() throws Exception {
        BoundingBox extent = new BoundingBox(0, 0, 100, 490);

        // should give 1x10 tiles, with bounds width expanded to 500
        int tileWidth = 20;
        int tileHeight = 10;

        SRS srs = SRS.getEPSG4326();

        boolean alignTopLeft = false;
        GridSet gridSet = GridSetFactory.createGridSet("test", srs, extent, alignTopLeft, 4, null,
                0.00028, tileWidth, tileHeight, false);

        assertEquals(new BoundingBox(0, 0, 100, 500), gridSet.getBounds());

        assertEquals(4, gridSet.getGridLevels().length);

        Grid grid0 = gridSet.getGridLevels()[0];

        long tilesWide = 1;
        long tilesHigh = 10;

        assertEquals(tilesWide, grid0.getNumTilesWide());
        assertEquals(tilesHigh, grid0.getNumTilesHigh());
        assertEquals(500D / tileHeight / tilesHigh, grid0.getResolution());

        alignTopLeft = true;
        gridSet = GridSetFactory.createGridSet("test", srs, extent, alignTopLeft, 4, null, 0.00028,
                tileWidth, tileHeight, false);

        BoundingBox bounds = gridSet.getBounds();
        assertEquals(new BoundingBox(0, -10, 100, 490), bounds);

        assertEquals("test", gridSet.getName());
        assertEquals(0D, gridSet.tileOrigin()[0]);
        assertEquals(490D, gridSet.tileOrigin()[1]);
    }
}