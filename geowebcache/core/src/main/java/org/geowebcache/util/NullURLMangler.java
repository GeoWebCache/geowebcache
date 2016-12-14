package org.geowebcache.util;

import org.apache.commons.lang.StringUtils;

public class NullURLMangler implements URLMangler {

    public String buildURL(String baseURL, String contextPath, String path) {
        final String context = StringUtils.strip(contextPath, "/");
         
        // if context is root ("/") then don't append it to prevent double slashes ("//") in return URLs  
        if ( context==null || context.isEmpty() ) {
            return StringUtils.strip(baseURL, "/") + "/" + StringUtils.strip(path, "/");
        } else {
            return StringUtils.strip(baseURL, "/") + "/" + context + "/" + StringUtils.strip(path, "/");
        }
    }

}