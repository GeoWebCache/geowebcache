package org.geowebcache.georss;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.verify;
import static org.easymock.classextension.EasyMock.replay;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import org.easymock.classextension.EasyMock;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.util.TestUtils;

import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

public class GeoRSSTileRangeBuilderTest extends TestCase {

    /**
     * Use the System property {@code org.geowebcache.debugToDisk} in order for the mask images
     * produces to be logged to the target directory
     */
    private static final boolean debugToDisk = Boolean.getBoolean("org.geowebcache.debugToDisk");

    private TileLayer layer;

    private String gridsetId;

    public void setUp() {
        layer = TestUtils.createWMSLayer("image/png", new GridSetBroker(false, false), 3, 3,
                new BoundingBox(-180, -90, 180, 90));
        gridsetId = layer.getGridSubsets().keySet().iterator().next();
    }

    public void testBuildTileRangeMask() throws Exception {

        TileGridFilterMatrix tileRangeMask = buildSampleFilterMatrix();

        assertNotNull(tileRangeMask);
        assertEquals(0, tileRangeMask.getStartLevel());
        assertEquals(11, tileRangeMask.getNumLevels());
        assertEquals(layer.getGridSubset(gridsetId).getCoverages().length, tileRangeMask
                .getNumLevels());

    }

    /**
     * Test for {@link TileGridFilterMatrix#getCoveredBounds(int)}
     * 
     * @throws Exception
     */
    public void testCoveredBounds() throws Exception {
        TileGridFilterMatrix tileRangeMask = buildSampleFilterMatrix();

        long[][] coverages = layer.getGridSubset(gridsetId).getCoverages();
        long[][] expectedGridCoverages = {// just as a reminder
        new long[] { 0, 0, 1, 0, 0 },//
                new long[] { 0, 0, 3, 1, 1 },//
                new long[] { 0, 0, 7, 3, 2 },//
                new long[] { 0, 0, 15, 7, 3 },//
                new long[] { 0, 0, 31, 15, 4 },//
                new long[] { 0, 0, 63, 31, 5 },//
                new long[] { 0, 0, 127, 63, 6 },//
                new long[] { 0, 0, 255, 127, 7 },//
                new long[] { 0, 0, 511, 255, 8 },//
                new long[] { 0, 0, 1023, 511, 9 },//
                new long[] { 0, 0, 2047, 1023, 10 } //
        };
        TestUtils.assertEquals(expectedGridCoverages, coverages);

        TestUtils.assertEquals(new long[] { 0, 0, 1, 0 }, tileRangeMask.getCoveredBounds(0));
        TestUtils.assertEquals(new long[] { 0, 0, 3, 1 }, tileRangeMask.getCoveredBounds(1));
        TestUtils.assertEquals(new long[] { 1, 0, 7, 3 }, tileRangeMask.getCoveredBounds(2));
        TestUtils.assertEquals(new long[] { 3, 0, 15, 6 }, tileRangeMask.getCoveredBounds(3));
        TestUtils.assertEquals(new long[] { 7, 0, 31, 12 }, tileRangeMask.getCoveredBounds(4));
        TestUtils.assertEquals(new long[] { 15, 0, 63, 24 }, tileRangeMask.getCoveredBounds(5));
        TestUtils.assertEquals(new long[] { 31, 0, 127, 48 }, tileRangeMask.getCoveredBounds(6));
        TestUtils.assertEquals(new long[] { 63, 0, 255, 96 }, tileRangeMask.getCoveredBounds(7));
        TestUtils.assertEquals(new long[] { 127, 0, 511, 192 }, tileRangeMask.getCoveredBounds(8));
        TestUtils.assertEquals(new long[] { 255, 0, 1023, 384 }, tileRangeMask.getCoveredBounds(9));
        TestUtils
                .assertEquals(new long[] { 511, 0, 2047, 768 }, tileRangeMask.getCoveredBounds(10));
    }

    /**
     * Once the matrix is built, test the expected tiles are marked as covered by the consumed
     * geometries
     * 
     * @throws Exception
     */
    public void testTileIsPresent() throws Exception {
        TileGridFilterMatrix tileRangeMask = buildSampleFilterMatrix();

        assertNotNull(tileRangeMask);
        assertEquals(0, tileRangeMask.getStartLevel());
        assertEquals(layer.getGridSubset(gridsetId).getCoverages().length, tileRangeMask
                .getNumLevels());

        // level 0
        assertEquals(true, tileRangeMask.isTileSet(0, 0, 0));
        assertEquals(true, tileRangeMask.isTileSet(1, 0, 0));

        // level 1
        assertEquals(false, tileRangeMask.isTileSet(0, 1, 1));
        assertEquals(true, tileRangeMask.isTileSet(1, 1, 1));
        assertEquals(true, tileRangeMask.isTileSet(1, 0, 1));

        // level 2
        assertEquals(false, tileRangeMask.isTileSet(0, 0, 2));
        assertEquals(false, tileRangeMask.isTileSet(0, 1, 2));
        assertEquals(true, tileRangeMask.isTileSet(1, 0, 2));
        assertEquals(false, tileRangeMask.isTileSet(0, 3, 2));
        assertEquals(true, tileRangeMask.isTileSet(7, 0, 2));

        // level 9 (coverage is 0, 0, 1023, 511, 9)
        assertEquals(false, tileRangeMask.isTileSet(0, 0, 9));// lower left
        assertEquals(false, tileRangeMask.isTileSet(0, 511, 9));// upper left
        assertEquals(false, tileRangeMask.isTileSet(1023, 511, 9));// upper right
        assertEquals(true, tileRangeMask.isTileSet(1023, 0, 9));// lower right

        assertEquals(true, tileRangeMask.isTileSet(511, 127, 9));// point location

        // line end point 1 LINESTRING(-90 -45, 90 45)
        assertEquals(true, tileRangeMask.isTileSet(255, 127, 9));
        // line end point 2 LINESTRING(-90 -45, 90 45)
        assertEquals(true, tileRangeMask.isTileSet(767, 383, 9));

        // center
        assertEquals(true, tileRangeMask.isTileSet(511, 255, 9));
    }

    public void testTileIsPresentBuffering() throws Exception {

        TileGridFilterMatrix tileRangeMask = buildSampleFilterMatrix();

        // level 5 (coverage is 0, 0, 63, 31)

        assertEquals(true, tileRangeMask.isTileSet(32, 23, 5));// point location

        assertEquals(true, tileRangeMask.isTileSet(31, 23, 5));// point's left
        assertEquals(true, tileRangeMask.isTileSet(33, 23, 5));// point's right
        assertEquals(true, tileRangeMask.isTileSet(32, 24, 5));// point's top
        assertEquals(true, tileRangeMask.isTileSet(32, 22, 5));// point's bottom

        assertEquals(true, tileRangeMask.isTileSet(31, 24, 5));// point's top left
        assertEquals(true, tileRangeMask.isTileSet(33, 24, 5));// point's top right
        assertEquals(true, tileRangeMask.isTileSet(31, 22, 5));// point's bottom left
        assertEquals(true, tileRangeMask.isTileSet(33, 22, 5));// point's bottom right
    }

    /**
     * maxMaskLevel is lower then max zoom level, then downsampling needs to be applied to
     * {@link TileGridFilterMatrix#isTileSet(long, long, int)}
     */
    public void testTileIsPresentWithSubSampling() throws Exception {

        final int maxMaskLevel = 3;
        TileGridFilterMatrix tileRangeMask = buildSampleFilterMatrix(maxMaskLevel);

        // level 5 (coverage is 0, 0, 63, 31)

        assertEquals(false, tileRangeMask.isTileSet(0, 0, 5));
        assertEquals(false, tileRangeMask.isTileSet(0, 31, 5));
        assertEquals(false, tileRangeMask.isTileSet(63, 31, 5));
        assertEquals(true, tileRangeMask.isTileSet(63, 0, 5));

        assertEquals(true, tileRangeMask.isTileSet(32, 23, 5));// point location

        assertEquals(true, tileRangeMask.isTileSet(31, 23, 5));// point's left
        assertEquals(true, tileRangeMask.isTileSet(33, 23, 5));// point's right
        assertEquals(true, tileRangeMask.isTileSet(32, 24, 5));// point's top
        assertEquals(true, tileRangeMask.isTileSet(32, 22, 5));// point's bottom

        assertEquals(true, tileRangeMask.isTileSet(31, 24, 5));// point's top left
        assertEquals(true, tileRangeMask.isTileSet(33, 24, 5));// point's top right
        assertEquals(true, tileRangeMask.isTileSet(31, 22, 5));// point's bottom left
        assertEquals(true, tileRangeMask.isTileSet(33, 22, 5));// point's bottom right
    }

    private TileGridFilterMatrix buildSampleFilterMatrix() throws Exception {
        return buildSampleFilterMatrix(10);
    }

    private TileGridFilterMatrix buildSampleFilterMatrix(final int maxMaskLevel) throws Exception {

        final Entry entries[] = createSampleEntries();
        final GeoRSSReader reader = EasyMock.createMock(GeoRSSReader.class);
        expect(reader.nextEntry()).andReturn(entries[0]);
        expect(reader.nextEntry()).andReturn(entries[1]);
        expect(reader.nextEntry()).andReturn(entries[2]);
        expect(reader.nextEntry()).andReturn(null);
        replay(reader);

        final GeoRSSTileRangeBuilder builder = new GeoRSSTileRangeBuilder(layer, gridsetId,
                maxMaskLevel);

        TileGridFilterMatrix tileRangeMask = builder.buildTileRangeMask(reader);

        logImages(new File("target"), tileRangeMask);

        verify(reader);
        return tileRangeMask;
    }

    private void logImages(final File target, final TileGridFilterMatrix matrix) throws IOException {
        if (!debugToDisk) {
            return;
        }

        BufferedImage[] byLevelMasks = matrix.getByLevelMasks();

        for (int i = 0; i < byLevelMasks.length; i++) {
            File output = new File(target, "level_" + i + ".tiff");
            System.out.println("--- writing " + output.getAbsolutePath() + "---");
            ImageIO.write(byLevelMasks[i], "TIFF", output);
        }
    }

    /**
     * Creates three sample georss feed entries in WGS84
     * <p>
     * <ul>
     * <li>A Polygon covering the lower right quadrant of the world
     * <li>A Point at {@code 0, 45}
     * <li>A LineString at {@code -90 -45, -90 45}
     * </ul>
     * </p>
     * 
     * @return
     */
    private Entry[] createSampleEntries() throws Exception {
        Entry[] entries = {//
        entry("POLYGON ((0 0, 0 -90, 180 -90, 180 0, 0 0))"),//
                entry("POINT(0 45)"),//
                entry("LINESTRING(-90 -45, 90 45)") };
        return entries;
    }

    private Entry entry(final String wkt) throws ParseException {
        Entry entry = new Entry();
        // entry.setSRS(crs);
        entry.setWhere(new WKTReader().read(wkt));
        return entry;
    }

}