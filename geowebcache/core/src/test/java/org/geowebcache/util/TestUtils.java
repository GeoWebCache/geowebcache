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
package org.geowebcache.util;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.wms.WMSLayer;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Assert;

public class TestUtils {

    private TestUtils() {
        // nothing to do
    }

    public static byte[] createFakeSourceImage(final WMSLayer layer, final String gridsetId) throws IOException {

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

    public static WMSLayer createWMSLayer(
            String format,
            GridSetBroker gridSetBroker,
            int metaTileFactorX,
            int metaTileFactorY,
            BoundingBox boundingBox) {

        String[] urls = {"http://localhost:38080/wms"};
        List<String> formatList = Collections.singletonList(format);

        Map<String, GridSubset> grids = new HashMap<>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), boundingBox, 0, 10);

        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = {metaTileFactorX, metaTileFactorY};

        WMSLayer layer = new WMSLayer(
                "test:layer",
                urls,
                "aStyle",
                "test:layer",
                formatList,
                grids,
                null,
                metaWidthHeight,
                "vendorparam=true",
                false,
                null);

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
        String errstr = "expected: " + Arrays.toString(expected) + ", actual: " + Arrays.toString(actual);
        if (expected.length != actual.length) {
            Assert.fail(errstr);
        }
        if (!Arrays.equals(expected, actual)) {
            Assert.fail(errstr);
        }
    }

    public static <T> Matcher<Optional<T>> notPresent() {
        return hasProperty("present", is(false));
    }

    public static <T> Matcher<Optional<T>> isPresent(Matcher<T> valueMatcher) {
        return new BaseMatcher<>() {

            @Override
            public boolean matches(Object item) {
                if (!(item instanceof Optional)) {
                    return false;
                } else {
                    return ((Optional<?>) item).map(valueMatcher::matches).orElse(false);
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Optional with value ").appendDescriptionOf(valueMatcher);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (!(item instanceof Optional)) {
                    description.appendText(item.toString() + " is not an Optional");
                } else if (((Optional<?>) item).isEmpty()) {
                    description.appendText("Value was not present");
                } else {
                    valueMatcher.describeMismatch(((Optional<?>) item).get(), description);
                }
            }
        };
    }

    public static <T> Matcher<Optional<T>> isPresent() {
        return hasProperty("present", is(true));
    }

    /** Match string matching a regular expression */
    public static Matcher<String> matchesRegex(String regex) {
        final Pattern p = Pattern.compile(regex);
        return new CustomMatcher<>("matching /" + regex + "/") {

            @Override
            public boolean matches(Object arg0) {
                return p.matcher((CharSequence) arg0).matches();
            }
        };
    }

    /**
     * Assert that an Optional is present, and returns its value if it is. Use this for checking the behaviour of the
     * unit under test.
     *
     * @return The optional's value
     */
    public static <T> T assertPresent(Optional<T> opt) throws AssertionError {
        return opt.orElseThrow(() -> new AssertionError("Optional was not present"));
    }

    /**
     * Require that an Optional is present, and returns its value if it is. Use this where the test should have ensured
     * that it will be present.
     *
     * @return The optional's value
     */
    public static <T> T requirePresent(Optional<T> opt) throws IllegalStateException {
        return opt.orElseThrow(() -> new IllegalStateException("Optional was not present and is required for test"));
    }
}
