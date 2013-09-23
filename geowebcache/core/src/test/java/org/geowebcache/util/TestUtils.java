/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.util;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import javax.imageio.ImageIO;

import junit.framework.Assert;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.wms.WMSLayer;

public class TestUtils {

    private TestUtils() {
        // nothing to do
    }

    public static byte[] createFakeSourceImage(final WMSLayer layer, final String gridsetId)
            throws IOException {

        int tileWidth = layer.getGridSubset(gridsetId).getGridSet().getTileWidth();
        int tileHeight = layer.getGridSubset(gridsetId).getGridSet().getTileHeight();

        int width = tileWidth * layer.getMetaTilingFactors()[0];
        int height = tileHeight * layer.getMetaTilingFactors()[1];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RenderedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        String formatName = layer.getMimeTypes().get(0).getInternalName();
        ImageIO.write(image, formatName, out);
        return out.toByteArray();
    }

    public static WMSLayer createWMSLayer(String format, GridSetBroker gridSetBroker,
            int metaTileFactorX, int metaTileFactorY, BoundingBox boundingBox) {

        String[] urls = { "http://localhost:38080/wms" };
        List<String> formatList = Collections.singletonList(format);

        Hashtable<String, GridSubset> grids = new Hashtable<String, GridSubset>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326,
                boundingBox, 0, 10);

        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = { metaTileFactorX, metaTileFactorY };

        WMSLayer layer = new WMSLayer("test:layer", urls, "aStyle", "test:layer", formatList,
                grids, null, metaWidthHeight, "vendorparam=true", false, null);

        layer.initialize(gridSetBroker);

        return layer;
    }

    public static WMSLayer createWMSLayer(final String format, final GridSetBroker gridSetBroker) {
        BoundingBox boundingBox = new BoundingBox(-30.0, 15.0, 45.0, 30);
        return createWMSLayer(format, gridSetBroker, 3, 3, boundingBox);
    }

    // public static SeedRequest createSeedRequest(WMSLayer tl, TYPE type, int zoomStart, int
    // zoomStop) {
    // String gridSet = tl.getGridSubsets().keySet().iterator().next();
    // BoundingBox bounds = null;
    // int threadCount = 1;
    // String format = tl.getMimeTypes().get(0).getFormat();
    // SeedRequest req = new SeedRequest(tl.getName(), bounds, gridSet, threadCount, zoomStart,
    // zoomStop, format, type, null);
    // return req;
    //
    // }

    public static String toStr(long[][] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(Arrays.toString(array[i]));
            if (i < array.length - 1) {
                sb.append(",");
            }
        }

        return sb.append("]").toString();
    }

    public static void assertEquals(long[][] expected, long[][] actual) {
        String errstr = "expected: " + toStr(expected) + ", actual: " + toStr(actual);
        if (expected.length != actual.length) {
            Assert.fail(errstr);
        }
        int len = expected.length;
        for (int i = 0; i < len; i++) {
            if (!Arrays.equals(expected[i], actual[i])) {
                Assert.fail("At index " + i + ": " + errstr);
            }
        }
    }

    public static void assertEquals(long[] expected, long[] actual) {
        String errstr = "expected: " + Arrays.toString(expected) + ", actual: "
                + Arrays.toString(actual);
        if (expected.length != actual.length) {
            Assert.fail(errstr);
        }
        if (!Arrays.equals(expected, actual)) {
            Assert.fail(errstr);
        }
    }

}
