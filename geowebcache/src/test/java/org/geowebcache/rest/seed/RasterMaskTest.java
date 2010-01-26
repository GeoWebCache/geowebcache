package org.geowebcache.rest.seed;

import static org.geowebcache.georss.GeoRSSTestUtils.buildSampleFilterMatrix;
import junit.framework.TestCase;

import org.geowebcache.georss.GeoRSSTestUtils;
import org.geowebcache.georss.TileGridFilterMatrix;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.util.TestUtils;

public class RasterMaskTest extends TestCase {
    /**
     * Use the System property {@code org.geowebcache.debugToDisk} in order for the mask images
     * produces to be logged to the target directory
     */
    private static final boolean debugToDisk = Boolean.getBoolean("org.geowebcache.debugToDisk");

    private TileLayer layer;

    private String gridsetId;

    private long[][] fullCoverage;

    public void setUp() {
        GeoRSSTestUtils.debugToDisk = debugToDisk;
        layer = TestUtils.createWMSLayer("image/png", new GridSetBroker(false, false), 3, 3,
                new BoundingBox(-180, -90, 180, 90));
        gridsetId = layer.getGridSubsets().keySet().iterator().next();
        fullCoverage = layer.getGridSubset(gridsetId).getCoverages();
    }

    /**
     * Once the matrix is built, test the expected tiles are marked as covered by the consumed
     * geometries
     * 
     * @throws Exception
     */
    public void testTileIsPresent() throws Exception {
        TileGridFilterMatrix mask = buildSampleFilterMatrix(layer, gridsetId);
        RasterMask tileRangeMask = new RasterMask(mask.getByLevelMasks(), fullCoverage, mask
                .getCoveredBounds());

        // level 0
        assertEquals(true, tileRangeMask.lookup(0, 0, 0));
        assertEquals(true, tileRangeMask.lookup(1, 0, 0));

        // level 1
        assertEquals(false, tileRangeMask.lookup(0, 1, 1));
        assertEquals(true, tileRangeMask.lookup(1, 1, 1));
        assertEquals(true, tileRangeMask.lookup(1, 0, 1));

        // level 2
        assertEquals(false, tileRangeMask.lookup(0, 0, 2));
        assertEquals(false, tileRangeMask.lookup(0, 1, 2));
        assertEquals(true, tileRangeMask.lookup(1, 0, 2));
        assertEquals(false, tileRangeMask.lookup(0, 3, 2));
        assertEquals(true, tileRangeMask.lookup(7, 0, 2));

        // level 9 (coverage is 0, 0, 1023, 511, 9)
        assertEquals(false, tileRangeMask.lookup(0, 0, 9));// lower left
        assertEquals(false, tileRangeMask.lookup(0, 511, 9));// upper left
        assertEquals(false, tileRangeMask.lookup(1023, 511, 9));// upper right
        assertEquals(true, tileRangeMask.lookup(1023, 0, 9));// lower right

        assertEquals(true, tileRangeMask.lookup(511, 127, 9));// point location

        // line end point 1 LINESTRING(-90 -45, 90 45)
        assertEquals(true, tileRangeMask.lookup(255, 127, 9));
        // line end point 2 LINESTRING(-90 -45, 90 45)
        assertEquals(true, tileRangeMask.lookup(767, 383, 9));

        // center
        assertEquals(true, tileRangeMask.lookup(511, 255, 9));
    }

    public void testTileIsPresentBuffering() throws Exception {

        TileGridFilterMatrix mask = buildSampleFilterMatrix(layer, gridsetId);
        RasterMask tileRangeMask = new RasterMask(mask.getByLevelMasks(), fullCoverage, mask
                .getCoveredBounds());

        // level 5 (coverage is 0, 0, 63, 31)

        assertEquals(true, tileRangeMask.lookup(32, 23, 5));// point location

        assertEquals(true, tileRangeMask.lookup(31, 23, 5));// point's left
        assertEquals(true, tileRangeMask.lookup(33, 23, 5));// point's right
        assertEquals(true, tileRangeMask.lookup(32, 24, 5));// point's top
        assertEquals(true, tileRangeMask.lookup(32, 22, 5));// point's bottom

        assertEquals(true, tileRangeMask.lookup(31, 24, 5));// point's top left
        assertEquals(true, tileRangeMask.lookup(33, 24, 5));// point's top right
        assertEquals(true, tileRangeMask.lookup(31, 22, 5));// point's bottom left
        assertEquals(true, tileRangeMask.lookup(33, 22, 5));// point's bottom right
    }

    /**
     * maxMaskLevel is lower then max zoom level, then downsampling needs to be applied to
     * {@link TileGridFilterMatrix#lookup(long, long, int)}
     */
    public void testTileIsPresentWithSubSampling() throws Exception {

        final int maxMaskLevel = 3;
        TileGridFilterMatrix mask = buildSampleFilterMatrix(layer, gridsetId, maxMaskLevel);
        RasterMask tileRangeMask = new RasterMask(mask.getByLevelMasks(), fullCoverage, mask
                .getCoveredBounds());

        // level 5 (coverage is 0, 0, 63, 31)

        assertEquals(false, tileRangeMask.lookup(0, 0, 5));
        assertEquals(false, tileRangeMask.lookup(0, 31, 5));
        assertEquals(false, tileRangeMask.lookup(63, 31, 5));
        assertEquals(true, tileRangeMask.lookup(63, 0, 5));

        assertEquals(true, tileRangeMask.lookup(32, 23, 5));// point location

        assertEquals(true, tileRangeMask.lookup(31, 23, 5));// point's left
        assertEquals(true, tileRangeMask.lookup(33, 23, 5));// point's right
        assertEquals(true, tileRangeMask.lookup(32, 24, 5));// point's top
        assertEquals(true, tileRangeMask.lookup(32, 22, 5));// point's bottom

        assertEquals(true, tileRangeMask.lookup(31, 24, 5));// point's top left
        assertEquals(true, tileRangeMask.lookup(33, 24, 5));// point's top right
        assertEquals(true, tileRangeMask.lookup(31, 22, 5));// point's bottom left
        assertEquals(true, tileRangeMask.lookup(33, 22, 5));// point's bottom right
    }

}
