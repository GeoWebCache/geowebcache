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
package org.geowebcache.georss;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class StaxGeoRSSReaderTest extends TestCase {

    public void testConstructor() throws Exception {
        try {
            new StaxGeoRSSReader(new StringReader("<not-a-feed/>"));
            fail("expected IAE on not a georss feed argument");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    public void testParsePointFeed() throws Exception {

        Reader feed = reader("point_feed.xml");
        StaxGeoRSSReader reader = new StaxGeoRSSReader(feed);

        List<Entry> entries = read(reader);

        assertEquals(3, entries.size());
        assertRequiredMembers(entries);

        assertTrue(entries.get(0).getWhere() instanceof Point);
        assertTrue(entries.get(1).getWhere() instanceof Point);
        assertTrue(entries.get(2).getWhere() instanceof Point);
    }

    public void testMultiGeometryTypesFeed() throws Exception {

        Reader feed = reader("mixedgeometries_feed.xml");
        StaxGeoRSSReader reader = new StaxGeoRSSReader(feed);

        List<Entry> entries = read(reader);

        assertEquals(6, entries.size());
        assertRequiredMembers(entries);

        assertTrue(entries.get(0).getWhere() instanceof Point);
        assertTrue(entries.get(1).getWhere() instanceof MultiPoint);
        assertTrue(entries.get(2).getWhere() instanceof Polygon);
        assertTrue(entries.get(3).getWhere() instanceof MultiPolygon);
        assertTrue(entries.get(4).getWhere() instanceof LineString);
        assertTrue(entries.get(5).getWhere() instanceof MultiLineString);
    }

    private void assertRequiredMembers(List<Entry> entries) {
        for (Entry e : entries) {
            assertNotNull(e.getUpdated());
            assertNotNull(e.getWhere());
        }
    }

    private List<Entry> read(final StaxGeoRSSReader reader) throws IOException {
        List<Entry> entries = new ArrayList<Entry>();
        Entry e;
        while ((e = reader.nextEntry()) != null) {
            entries.add(e);
        }
        return entries;
    }

    /**
     * @param fileName
     *            file name to create the java.io.Reader for, located at {@code <this
     *            package>/test-data/<fileName>}
     * @return a reader over {@code test-data/fileName}
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     */
    private Reader reader(final String fileName) throws FileNotFoundException,
            UnsupportedEncodingException {

        InputStream stream = getClass().getResourceAsStream("test-data/" + fileName);
        if (stream == null) {
            throw new FileNotFoundException("test-data/" + fileName);
        }
        return new BufferedReader(new InputStreamReader(stream, "UTF-8"));
    }
}
