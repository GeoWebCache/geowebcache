package org.geowebcache.georss;

import java.io.StringReader;

import junit.framework.TestCase;

public class StaxGeoRSSReaderTest extends TestCase {

    public void testConstructor() throws Exception {
        try {
            new StaxGeoRSSReader(new StringReader("<not-a-feed/>"));
            fail("expected IAE on not a georss feed argument");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }
}
