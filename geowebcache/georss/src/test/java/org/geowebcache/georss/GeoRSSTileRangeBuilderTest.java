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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.geowebcache.georss.RasterMaskTestUtils.buildSampleFilterMatrix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.util.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GeoRSSTileRangeBuilderTest {

    /**
     * Use the System property {@code org.geowebcache.debugToDisk} in order for the mask images produces to be logged to
     * the target directory
     */
    private static final boolean debugToDisk = Boolean.getBoolean("org.geowebcache.debugToDisk");

    private TileLayer layer;

    private String gridsetId;

    @Before
    public void setUp() {
        RasterMaskTestUtils.debugToDisk = debugToDisk;
        layer = TestUtils.createWMSLayer(
                "image/png",
                new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false))),
                3,
                3,
                new BoundingBox(-180, -90, 180, 90));
        gridsetId = layer.getGridSubsets().iterator().next();
    }

    @Test
    public void testBuildTileRangeMask() throws Exception {

        GeometryRasterMaskBuilder tileRangeMask = buildSampleFilterMatrix(layer, gridsetId);

        Assert.assertNotNull(tileRangeMask);
        Assert.assertEquals(0, tileRangeMask.getStartLevel());
        Assert.assertEquals(11, tileRangeMask.getNumLevels());
        Assert.assertEquals(layer.getGridSubset(gridsetId).getCoverages().length, tileRangeMask.getNumLevels());
    }

    /** Test for {@link GeometryRasterMaskBuilder#getCoveredBounds(int)} */
    @Test
    public void testCoveredBounds() throws Exception {
        GeometryRasterMaskBuilder tileRangeMask = buildSampleFilterMatrix(layer, gridsetId);

        long[][] coverages = layer.getGridSubset(gridsetId).getCoverages();
        long[][] expectedGridCoverages = { // just as a reminder
            new long[] {0, 0, 1, 0, 0}, //
            new long[] {0, 0, 3, 1, 1}, //
            new long[] {0, 0, 7, 3, 2}, //
            new long[] {0, 0, 15, 7, 3}, //
            new long[] {0, 0, 31, 15, 4}, //
            new long[] {0, 0, 63, 31, 5}, //
            new long[] {0, 0, 127, 63, 6}, //
            new long[] {0, 0, 255, 127, 7}, //
            new long[] {0, 0, 511, 255, 8}, //
            new long[] {0, 0, 1023, 511, 9}, //
            new long[] {0, 0, 2047, 1023, 10} //
        };
        TestUtils.assertEquals(expectedGridCoverages, coverages);

        TestUtils.assertEquals(new long[] {0, 0, 1, 0, 0}, tileRangeMask.getCoveredBounds(0));
        TestUtils.assertEquals(new long[] {0, 0, 3, 1, 1}, tileRangeMask.getCoveredBounds(1));
        TestUtils.assertEquals(new long[] {1, 0, 7, 3, 2}, tileRangeMask.getCoveredBounds(2));
        TestUtils.assertEquals(new long[] {3, 0, 15, 6, 3}, tileRangeMask.getCoveredBounds(3));
        TestUtils.assertEquals(new long[] {7, 0, 31, 12, 4}, tileRangeMask.getCoveredBounds(4));
        TestUtils.assertEquals(new long[] {15, 0, 63, 24, 5}, tileRangeMask.getCoveredBounds(5));
        TestUtils.assertEquals(new long[] {31, 0, 127, 48, 6}, tileRangeMask.getCoveredBounds(6));
        TestUtils.assertEquals(new long[] {63, 0, 255, 96, 7}, tileRangeMask.getCoveredBounds(7));
        TestUtils.assertEquals(new long[] {127, 0, 511, 192, 8}, tileRangeMask.getCoveredBounds(8));
        TestUtils.assertEquals(new long[] {255, 0, 1023, 384, 9}, tileRangeMask.getCoveredBounds(9));
        TestUtils.assertEquals(new long[] {511, 0, 2047, 768, 10}, tileRangeMask.getCoveredBounds(10));
    }

    @Test
    public void testLatestUpdate() throws IOException, XMLStreamException, FactoryConfigurationError {
        assertLatestUpdate("2005-08-17T07:02:34Z", "point_feed.xml");
        assertLatestUpdate("2010-08-17T07:02:32Z", "mixedgeometries_feed.xml");
    }

    private void assertLatestUpdate(String expected, String fileName)
            throws IOException, XMLStreamException, FactoryConfigurationError {

        try (InputStream stream = getClass().getResourceAsStream("test-data/" + fileName);
                Reader feed = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            StaxGeoRSSReader reader = new StaxGeoRSSReader(feed);
            GeoRSSTileRangeBuilder b = new GeoRSSTileRangeBuilder(layer, gridsetId, 10);
            b.buildTileRangeMask(reader, null);
            Assert.assertEquals(expected, b.getLastEntryUpdate());
        }
    }
}
