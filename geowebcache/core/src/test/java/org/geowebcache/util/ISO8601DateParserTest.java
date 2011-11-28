package org.geowebcache.util;

import java.sql.Timestamp;
import java.util.Date;

import junit.framework.TestCase;

public class ISO8601DateParserTest extends TestCase {

    public void testDateParsing() throws Exception {
        
        // remove milliseconds
        long t = System.currentTimeMillis();
        t = t - (t % 1000);
        Date d = new Date(t);
        
        String s = ISO8601DateParser.toString(d);

        Date d2 = ISO8601DateParser.parse(s);
        
        assertEquals(d, d2);
    }

    public void testTimestampParsing() throws Exception {
        
        long t = System.currentTimeMillis();
        t = t - (t % 1000);
        Timestamp ts = new Timestamp(t);
        
        String s = ISO8601DateParser.toString(ts);

        Timestamp ts2 = new Timestamp(ISO8601DateParser.parse(s).getTime());
        
        assertEquals(ts, ts2);
    }
}
