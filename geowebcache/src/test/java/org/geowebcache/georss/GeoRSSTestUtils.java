package org.geowebcache.georss;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.verify;
import static org.easymock.classextension.EasyMock.replay;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.easymock.classextension.EasyMock;
import org.geowebcache.layer.TileLayer;

import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

public class GeoRSSTestUtils {

    public static boolean debugToDisk;

    public static TileGridFilterMatrix buildSampleFilterMatrix(final TileLayer layer,
            final String gridsetId) throws Exception {
        return buildSampleFilterMatrix(layer, gridsetId, 10);
    }

    public static TileGridFilterMatrix buildSampleFilterMatrix(final TileLayer layer,
            final String gridsetId, final int maxMaskLevel) throws Exception {

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

    public static void logImages(final File target, final TileGridFilterMatrix matrix)
            throws IOException {
        if (debugToDisk) {
            BufferedImage[] byLevelMasks = matrix.getByLevelMasks();

            for (int i = 0; i < byLevelMasks.length; i++) {
                File output = new File(target, "level_" + i + ".tiff");
                System.out.println("--- writing " + output.getAbsolutePath() + "---");
                ImageIO.write(byLevelMasks[i], "TIFF", output);
            }
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
    private static Entry[] createSampleEntries() throws Exception {
        Entry[] entries = {//
        entry("POLYGON ((0 0, 0 -90, 180 -90, 180 0, 0 0))"),//
                entry("POINT(0 45)"),//
                entry("LINESTRING(-90 -45, 90 45)") };
        return entries;
    }

    private static Entry entry(final String wkt) throws ParseException {
        Entry entry = new Entry();
        // entry.setSRS(crs);
        entry.setWhere(new WKTReader().read(wkt));
        return entry;
    }

}
