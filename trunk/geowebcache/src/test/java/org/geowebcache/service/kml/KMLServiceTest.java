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
    	assertTrue(retVals[2].equals("kml"));
    	assertTrue(retVals[3] == null);
    }
    
    public void test2ParseRequest() throws Exception {
    	String[] retVals = KMLService.parseRequest("/kml/topp:states.jpeg.kml");
    	
    	assertTrue(retVals[0].equals("topp:states"));
    	assertTrue(retVals[1].length() == 0);
    	assertTrue(retVals[2].equals("kml"));
    	assertTrue(retVals[3].equals("jpeg"));
    }
}
