package org.geowebcache.grid;


import junit.framework.TestCase;

public class BoundingBoxTest extends TestCase {

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
    	BoundingBox bbox = new BoundingBox(-180.0,-90.0,180.0,90.0);
    	assert(bbox.isSane());
    	
    	String bboxStr = bbox.toString();
    	if(bboxStr.equalsIgnoreCase("-180.0,-90.0,180.0,90.0")) {
    		assertTrue(true);
    	} else {
    		assertTrue(false);
    	}
    }
    
    public void testBBOXScale() throws Exception {
        BoundingBox bbox = new BoundingBox(-180.0,-90.0,180.0,90.0);
        
        BoundingBox copy = new BoundingBox(bbox);
        bbox.scale(1.0);
        bbox.scale(0.5);
        bbox.scale(2.0);
        
        assert(bbox.isSane());
        assert(bbox.equals(copy));
    }
}