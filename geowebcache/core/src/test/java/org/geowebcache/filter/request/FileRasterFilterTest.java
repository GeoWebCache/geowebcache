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
 * <p>Copyright 2026
 */
package org.geowebcache.filter.request;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyArray;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.TemporaryFolder;

public class FileRasterFilterTest {

    // no colon, Windows paths reject it
    private static final String GRID_SET = "grid";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private TileLayer layer;

    @Before
    public void setup() {
        // a 2x2 tiles coverage at level 0
        GridSubset subset = mock(GridSubset.class);
        when(subset.getCoverage(0)).thenReturn(new long[] {0, 0, 1, 1, 0});
        layer = mock(TileLayer.class);
        when(layer.getGridSubset(GRID_SET)).thenReturn(subset);
    }

    @Test
    public void testUnsupportedExtension() {
        assertUpdateFails(newFilter("shp"), new byte[] {0}, GRID_SET, 0, "Unsupported raster file extension: shp");
    }

    @Test
    public void testMissingExtension() {
        assertUpdateFails(newFilter(null), new byte[] {0}, GRID_SET, 0, "Unsupported raster file extension: null");
    }

    @Test
    public void testUnknownGridSet() throws Exception {
        // typo, the layer only has "grid"
        assertRejected(newFilter("png"), png(2, 2), "gird", 0, "Unknown grid set gird");
    }

    @Test
    public void testZoomOutOfRange() throws Exception {
        assertRejected(newFilter("png"), png(2, 2), GRID_SET, 3, "Zoom level 3 is out of range");
    }

    @Test
    public void testNotARaster() {
        assertRejected(newFilter("png"), "<html></html>".getBytes(UTF_8), GRID_SET, 0, "is not a png image");
    }

    @Test
    public void testFormatMismatch() throws Exception {
        assertRejected(newFilter("tiff"), png(2, 2), GRID_SET, 0, "is not a tiff image");
    }

    @Test
    public void testWrongSize() throws Exception {
        assertRejected(newFilter("png"), png(3, 3), GRID_SET, 0, "has dimensions 3,3, expected 2,2");
    }

    @Test
    public void testTruncatedImage() throws Exception {
        // valid PNG signature, header cut short
        byte[] truncated = Arrays.copyOf(png(2, 2), 20);
        assertRejected(newFilter("png"), truncated, GRID_SET, 0, "is not a readable png image");
    }

    @Test
    public void testInvalidUploadKeepsMatrix() throws Exception {
        FileRasterFilter filter = newFilter("png");
        byte[] valid = png(2, 2);
        filter.update(valid, layer, GRID_SET, 0);

        assertThrows(IllegalArgumentException.class, () -> filter.update(png(3, 3), layer, GRID_SET, 0));
        Path matrix = temp.getRoot().toPath().resolve("test_grid_0.png");
        assertArrayEquals(valid, Files.readAllBytes(matrix));
        // no temp file left behind
        assertThat(temp.getRoot().list(), arrayContaining("test_grid_0.png"));
        assertEquals(2, filter.matrices.get(GRID_SET)[0].getWidth());
    }

    /** Checks the update rejects the request as a client error, see {@link #assertFails}. */
    private void assertRejected(FileRasterFilter filter, byte[] data, String gridSetId, int z, String message) {
        assertFails(IllegalArgumentException.class, () -> filter.update(data, layer, gridSetId, z), message);
    }

    /** Checks the update fails as a server error, see {@link #assertFails}. */
    private void assertUpdateFails(FileRasterFilter filter, byte[] data, String gridSetId, int z, String message) {
        assertFails(GeoWebCacheException.class, () -> filter.update(data, layer, gridSetId, z), message);
    }

    /** Checks the update fails with the given message and leaves the storage folder empty. */
    private void assertFails(Class<? extends Exception> type, ThrowingRunnable update, String message) {
        Exception e = assertThrows(type, update);
        assertThat(e.getMessage(), containsString(message));
        assertThat(temp.getRoot().list(), emptyArray());
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY), "png", out);
        return out.toByteArray();
    }

    private FileRasterFilter newFilter(String extension) {
        FileRasterFilter filter = new FileRasterFilter();
        filter.setName("test");
        filter.setStoragePath(temp.getRoot().getPath());
        filter.setFileExtension(extension);
        filter.setZoomStop(2);
        return filter;
    }
}
