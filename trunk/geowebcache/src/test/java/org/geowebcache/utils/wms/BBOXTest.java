package org.geowebcache.utils.wms;


import junit.framework.TestCase;

import org.geowebcache.util.wms.BBOX;

public class BBOXTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Verifies that this functions output remains
     * as expected, since we communicate a lot using strings.
     * 
     * Ff not you should figure out where it is used.
     *  
     * @throws Exception
     */
    public void testBBOX() throws Exception {
    	BBOX bbox = new BBOX(-180.0,-90.0,180.0,90.0);
    	assert(bbox.isSane());
    	
    	String bboxStr = bbox.toString();
    	if(bboxStr.equalsIgnoreCase("-180.0,-90.0,180.0,90.0")) {
    		assertTrue(true);
    	} else {
    		assertTrue(false);
    	}
    }
}