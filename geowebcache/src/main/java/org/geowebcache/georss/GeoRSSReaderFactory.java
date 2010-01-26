package org.geowebcache.georss;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

class GeoRSSReaderFactory {

    public GeoRSSReader createReader(final URL feedUrl) throws IOException {
        return createFakeReader();
        // URLConnection conn = feedUrl.openConnection();
        // String contentEncoding = conn.getContentEncoding();
        // if (contentEncoding == null) {
        // contentEncoding = "UTF-8";
        // }
        // InputStream in = conn.getInputStream();
        // Reader reader = new BufferedReader(new InputStreamReader(in, contentEncoding));
        // return createReader(reader);
    }

    public GeoRSSReader createReader(final Reader feed) throws IOException {
        GeoRSSReader reader;
        try {
            reader = new StaxGeoRSSReader(feed);
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        } catch (FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }
        return reader;
    }

    private GeoRSSReader createFakeReader() {

        final Iterator<Entry> entries = createFakeEntries().iterator();
        GeoRSSReader fakeReader = new GeoRSSReader() {
            public Entry nextEntry() throws IOException {
                return entries.hasNext() ? entries.next() : null;
            }
        };
        return fakeReader;
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
    private List<Entry> createFakeEntries() {
        Entry[] entries = {//
        entry("POLYGON ((-115 31, -115 37, -108 37, -108 31, -115 31))"),// around arizona
                entry("POINT(-105 38)"),// mid colorado
                entry("LINESTRING(-79 42, -73 44)") // amlmost croses NY
        };

        return Arrays.asList(entries);
    }

    private Entry entry(final String wkt) {
        Entry entry = new Entry();
        entry.setId("Enrty-" + System.currentTimeMillis());
        try {
            entry.setLink(new URI("http://example.com"));
        } catch (URISyntaxException e) {
            //
        }
        entry.setTitle("Fake entry " + wkt);
        entry.setUpdated(new Date());
        // entry.setSRS(crs);
        try {
            entry.setWhere(new WKTReader().read(wkt));
        } catch (ParseException e) {
            throw new IllegalArgumentException(wkt, e);
        }
        return entry;
    }
}
