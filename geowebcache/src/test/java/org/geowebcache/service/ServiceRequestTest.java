package org.geowebcache.service;

import junit.framework.TestCase;

public class ServiceRequestTest extends TestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Tests 
     * 
     * @throws Exception
     */
    public void test1Flags() throws Exception {
        ServiceRequest servReq = new ServiceRequest("layer:name");
        
    	assertTrue(servReq.getLayerIdent().equalsIgnoreCase("layer:name"));
        
        // Test the default type
    	assertFalse(servReq.getFlag(ServiceRequest.SERVICE_REQUEST_DIRECT));
    	assertFalse(servReq.getFlag(ServiceRequest.SERVICE_REQUEST_METATILE));
        
        // Set the flag for later testing
        servReq.setFlag(true, ServiceRequest.SERVICE_REQUEST_DIRECT);
        
        // Test toggling
        assertFalse(servReq.getFlag(ServiceRequest.SERVICE_REQUEST_USE_JAI));
        servReq.setFlag(true, ServiceRequest.SERVICE_REQUEST_USE_JAI);
        assertTrue(servReq.getFlag(ServiceRequest.SERVICE_REQUEST_USE_JAI));
        servReq.setFlag(false, ServiceRequest.SERVICE_REQUEST_USE_JAI);
        assertFalse(servReq.getFlag(ServiceRequest.SERVICE_REQUEST_USE_JAI));
        
        // Test double toggling
        servReq.setFlag(true, ServiceRequest.SERVICE_REQUEST_USE_JAI);
        assertTrue(servReq.getFlag(ServiceRequest.SERVICE_REQUEST_USE_JAI));
        servReq.setFlag(true, ServiceRequest.SERVICE_REQUEST_USE_JAI);
        assertTrue(servReq.getFlag(ServiceRequest.SERVICE_REQUEST_USE_JAI));
        
        // Make sure we didn't break anything during that
        assertTrue(servReq.getFlag(ServiceRequest.SERVICE_REQUEST_DIRECT));
        assertFalse(servReq.getFlag(ServiceRequest.SERVICE_REQUEST_METATILE));
    }
}
