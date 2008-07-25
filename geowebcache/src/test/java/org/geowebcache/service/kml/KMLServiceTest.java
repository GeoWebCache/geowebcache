package org.geowebcache.service.kml;

import junit.framework.TestCase;

public class KMLServiceTest extends TestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Tests 
     * 
     * @throws Exception
     */
    public void test1ParseRequest() throws Exception {
    	String[] retVals = KMLService.parseRequest("/kml/topp:states.kml");
    	
    	assertTrue(retVals[0].equals("topp:states"));
    	assertTrue(retVals[1].length() == 0);
    	assertTrue(retVals[2] == null);
    	assertTrue(retVals[3].equals("kml"));
    }
    
    public void test2ParseRequest() throws Exception {
    	String[] retVals = KMLService.parseRequest("/kml/topp:states.jpeg.kml");
    	
    	assertTrue(retVals[0].equals("topp:states"));
    	assertTrue(retVals[1].length() == 0);
    	assertTrue(retVals[2].equals("jpeg"));
    	assertTrue(retVals[3].equals("kml"));
    }
    
    public void test3ParseRequest() throws Exception {
        String[] retVals = KMLService.parseRequest("/kml/topp:states/x1y2z3.jpeg.kml");
        
        assertTrue(retVals[0].equals("topp:states"));
        assertTrue(retVals[1].equals("x1y2z3"));
        assertTrue(retVals[2].equals("jpeg"));
        assertTrue(retVals[3].equals("kml"));
        int[] test = {1,2,3};
        this.assertEquals(test[0], KMLService.parseGridLocString(retVals[1])[0]);
        this.assertEquals(test[1], KMLService.parseGridLocString(retVals[1])[1]);
        this.assertEquals(test[2], KMLService.parseGridLocString(retVals[1])[2]);
    }
}
