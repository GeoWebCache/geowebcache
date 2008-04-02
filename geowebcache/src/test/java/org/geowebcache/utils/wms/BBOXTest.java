package org.geowebcache.utils.wms;


import java.util.Arrays;

import org.geowebcache.util.wms.BBOX;

import junit.framework.TestCase;

public class BBOXTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * GetCapabilitiesConfiguration is dependent on the toString
     * function, this verifies that this functions output remains
     * as expected.
     *  
     * @throws Exception
     */
    public void testBBOX() throws Exception {
    	BBOX bbox = new BBOX(-180.0,-90.0,180.0,90.0);
    	assert(bbox.isSane());
    	
    	String bboxStr = bbox.toString();
    	if(bboxStr.equalsIgnoreCase("-180.0,-90.0,180.0,90.0")) {
    		assert(true);
    	} else {
    		assert(false);
    	}
    }
}