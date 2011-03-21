package org.geowebcache.rest;

import java.util.Map;

/**
 * This is the extension point for REST modules to register themselves with Geoserver. The mapping
 * should have path specifications compatible with the REST Router class for keys, and Restlets for
 * values.
 * 
 * @author David Winslow <dwinslow@openplans.org>
 */
public class RESTMapping {
    private Map<String, Object> myRoutes;

    public void setRoutes(Map<String, Object> m) {
        // TODO: Check this and throw an error for bad data
        myRoutes = m;
    }

    public Map<String, Object> getRoutes() {
        return myRoutes;
    }
}
