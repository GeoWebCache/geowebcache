package org.geowebcache.service.kml;

import junit.framework.TestCase;

public class KMLServiceTest extends TestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /** Tests */
    public void test1ParseRequest() throws Exception {
        String[] retVals = KMLService.parseRequest("/kml/topp:states.kml");

        assertEquals("topp:states", retVals[0]);
        assertEquals(0, retVals[1].length());
        assertEquals("kml", retVals[2]);
        assertNull(retVals[3]);
    }

    public void test2ParseRequest() throws Exception {
        String[] retVals = KMLService.parseRequest("/kml/topp:states.jpeg.kml");

        assertEquals("topp:states", retVals[0]);
        assertEquals(0, retVals[1].length());
        assertEquals("jpeg", retVals[2]);
        assertEquals("kml", retVals[3]);
    }

    public void test3ParseRequest() throws Exception {
        String[] retVals = KMLService.parseRequest("/kml/topp:states/x1y2z3.jpeg.kml");

        assertEquals("topp:states", retVals[0]);
        assertEquals("x1y2z3", retVals[1]);
        assertEquals("jpeg", retVals[2]);
        assertEquals("kml", retVals[3]);
        int[] test = {1, 2, 3};
        assertEquals(test[0], KMLService.parseGridLocString(retVals[1])[0]);
        assertEquals(test[1], KMLService.parseGridLocString(retVals[1])[1]);
        assertEquals(test[2], KMLService.parseGridLocString(retVals[1])[2]);
    }
}
