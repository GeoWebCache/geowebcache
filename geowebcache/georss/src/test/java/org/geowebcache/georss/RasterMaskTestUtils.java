/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.georss;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.geotools.util.logging.Logging;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

public class RasterMaskTestUtils {

    static final Logger LOG = Logging.getLogger(RasterMaskTestUtils.class.getName());

    public static boolean debugToDisk;

    public static GeometryRasterMaskBuilder buildSampleFilterMatrix(final TileLayer layer, final String gridsetId)
            throws Exception {
        return buildSampleFilterMatrix(layer, gridsetId, 10);
    }

    public static GeometryRasterMaskBuilder buildSampleFilterMatrix(
            final TileLayer layer, final String gridsetId, final int maxMaskLevel) throws Exception {

        final Geometry[] entries = createSampleEntries();

        final GridSubset gridSubset =
                layer.getGridSubset(layer.getGridSubsets().iterator().next());
        final int[] metaTilingFactors = layer.getMetaTilingFactors();
        GeometryRasterMaskBuilder matrix = new GeometryRasterMaskBuilder(gridSubset, metaTilingFactors, maxMaskLevel);

        try {
            for (Geometry geom : entries) {
                matrix.setMasksForGeometry(geom);
            }
        } finally {
            matrix.disposeGraphics();
        }

        logImages(new File("target"), matrix);

        return matrix;
    }

    public static void logImages(final File target, final GeometryRasterMaskBuilder matrix) throws IOException {
        if (debugToDisk) {
            BufferedImage[] byLevelMasks = matrix.getByLevelMasks();

            for (int i = 0; i < byLevelMasks.length; i++) {
                File output = new File(target, "level_" + i + ".tiff");
                LOG.info("--- writing " + output.getAbsolutePath() + "---");
                ImageIO.write(byLevelMasks[i], "TIFF", output);
            }
        }
    }

    /**
     * Creates three sample georss feed entries in WGS84
     *
     * <p>
     *
     * <ul>
     *   <li>A Polygon covering the lower right quadrant of the world
     *   <li>A Point at {@code 0, 45}
     *   <li>A LineString at {@code -90 -45, -90 45}
     * </ul>
     */
    private static Geometry[] createSampleEntries() throws Exception {
        Geometry[] entries = { //
            entry("POLYGON ((0 0, 0 -90, 180 -90, 180 0, 0 0))"), //
            entry("POINT(0 45)"), //
            entry("LINESTRING(-90 -45, 90 45)")
        };
        return entries;
    }

    private static Geometry entry(final String wkt) throws ParseException {
        Geometry geometry = new WKTReader().read(wkt);
        return geometry;
    }
}
