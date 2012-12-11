/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geowebcache.util;

/**
 * subset copied from org.geoserver.ows.URLMangler
 *
 * This hook allows others to plug in custom url generation.
 *
 */
public interface URLMangler {

    /**
     * Allows for a custom url generation strategy
     * @param baseURL the base url - contains the url up to the domain and port
     * @param contextPath the servlet context path, like /geoserver/gwc
     * @param path the remaining path after the context path
     * @return the full generated url from the pieces
     */
    public String buildURL(String baseURL, String contextPath, String path);

}
