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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

public class StaxGeoRSSReaderTest {

    @Test
    public void testConstructor() throws Exception {
        try {
            new StaxGeoRSSReader(new StringReader("<not-a-feed/>"));
            Assert.fail("expected IAE on not a georss feed argument");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testParsePointFeed() throws Exception {

        try (Reader feed = reader("point_feed.xml")) {
            StaxGeoRSSReader reader = new StaxGeoRSSReader(feed);

            List<Entry> entries = read(reader);

            Assert.assertEquals(3, entries.size());
            assertRequiredMembers(entries);

            Assert.assertTrue(entries.get(0).getWhere() instanceof Point);
            Assert.assertTrue(entries.get(1).getWhere() instanceof Point);
            Assert.assertTrue(entries.get(2).getWhere() instanceof Point);
        }
    }

    @Test
    public void testMultiGeometryTypesFeed() throws Exception {

        try (Reader feed = reader("mixedgeometries_feed.xml")) {
            StaxGeoRSSReader reader = new StaxGeoRSSReader(feed);

            List<Entry> entries = read(reader);

            Assert.assertEquals(6, entries.size());
            assertRequiredMembers(entries);

            Assert.assertTrue(entries.get(0).getWhere() instanceof Point);
            Assert.assertTrue(entries.get(1).getWhere() instanceof MultiPoint);
            Assert.assertTrue(entries.get(2).getWhere() instanceof Polygon);
            Assert.assertTrue(entries.get(3).getWhere() instanceof MultiPolygon);
            Assert.assertTrue(entries.get(4).getWhere() instanceof LineString);
            Assert.assertTrue(entries.get(5).getWhere() instanceof MultiLineString);
        }
    }

    private void assertRequiredMembers(List<Entry> entries) {
        for (Entry e : entries) {
            Assert.assertNotNull(e.getUpdated());
            Assert.assertNotNull(e.getWhere());
        }
    }

    private List<Entry> read(final StaxGeoRSSReader reader) throws IOException {
        List<Entry> entries = new ArrayList<>();
        Entry e;
        while ((e = reader.nextEntry()) != null) {
            entries.add(e);
        }
        return entries;
    }

    /**
     * @param fileName file name to create the java.io.Reader for, located at {@code <this
     *     package>/test-data/<fileName>}
     * @return a reader over {@code test-data/fileName}
     */
    private Reader reader(final String fileName) throws FileNotFoundException, UnsupportedEncodingException {

        InputStream stream = getClass().getResourceAsStream("test-data/" + fileName);
        if (stream == null) {
            throw new FileNotFoundException("test-data/" + fileName);
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
