package org.geowebcache.service.kml;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class KMLServiceTest {
    @Before
    public void setUp() throws Exception {}

    /** Tests */
    @Test
    public void test1ParseRequest() throws Exception {
        String[] retVals = KMLService.parseRequest("/kml/topp:states.kml");

        Assert.assertEquals("topp:states", retVals[0]);
        Assert.assertEquals(0, retVals[1].length());
        Assert.assertEquals("kml", retVals[2]);
        Assert.assertNull(retVals[3]);
    }

    @Test
    public void test2ParseRequest() throws Exception {
        String[] retVals = KMLService.parseRequest("/kml/topp:states.jpeg.kml");

        Assert.assertEquals("topp:states", retVals[0]);
        Assert.assertEquals(0, retVals[1].length());
        Assert.assertEquals("jpeg", retVals[2]);
        Assert.assertEquals("kml", retVals[3]);
    }

    @Test
    public void test3ParseRequest() throws Exception {
        String[] retVals = KMLService.parseRequest("/kml/topp:states/x1y2z3.jpeg.kml");

        Assert.assertEquals("topp:states", retVals[0]);
        Assert.assertEquals("x1y2z3", retVals[1]);
        Assert.assertEquals("jpeg", retVals[2]);
        Assert.assertEquals("kml", retVals[3]);
        int[] test = {1, 2, 3};
        Assert.assertEquals(test[0], KMLService.parseGridLocString(retVals[1])[0]);
        Assert.assertEquals(test[1], KMLService.parseGridLocString(retVals[1])[1]);
        Assert.assertEquals(test[2], KMLService.parseGridLocString(retVals[1])[2]);
    }
}
