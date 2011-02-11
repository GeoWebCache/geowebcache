package org.geowebcache.util;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

public class GWCVars {

    private static final Log log = LogFactory.getLog(GWCVars.class);

    // everything here requires initialization

    public static final int CACHE_VALUE_UNSET = 0;

    public static final int CACHE_DISABLE_CACHE = -1;

    public static final int CACHE_NEVER_EXPIRE = -2;

    public static final int CACHE_USE_WMS_BACKEND_VALUE = -4;

    public static String findEnvVar(ApplicationContext context, String varStr) {
        ServletContext serlvCtx = null;
        if (context instanceof WebApplicationContext) {
            serlvCtx = ((WebApplicationContext) context).getServletContext();
        }

        final String[] typeStrs = { "Java environment variable ", "servlet context parameter ",
                "system environment variable " };

        String value = null;

        for (int j = 0; j < typeStrs.length && value == null; j++) {
            String typeStr = typeStrs[j];

            switch (j) {
            case 0:
                value = System.getProperty(varStr);
                break;
            case 1:
                if (serlvCtx != null) {
                    value = serlvCtx.getInitParameter(varStr);
                }
                break;
            case 2:
                value = System.getenv(varStr);
                break;
            }

            if (value != null) {
                if (varStr.equals(DefaultStorageFinder.GWC_METASTORE_PASSWORD)) {
                    log.info("Found " + typeStr + " for " + varStr + " set to <hidden>");
                } else {
                    log.info("Found " + typeStr + " for " + varStr + " set to " + value);
                }
            }
        }

        return value;
    }
}
