package org.geowebcache.util;

import org.apache.commons.lang.StringUtils;

public class NullURLMangler implements URLMangler {

    public String buildURL(String baseURL, String contextPath, String path) {
        return StringUtils.strip(baseURL, "/") + "/" + StringUtils.strip(contextPath, "/") + "/"
                + StringUtils.stripStart(path, "/");
    }

}
