/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */
package org.geowebcache.util;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.wms.WebMapServer;
import org.geotools.ows.ServiceException;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.CacheFactory;
import org.springframework.web.context.WebApplicationContext;

/**
 * This is just a subclass of the GetCapabilities object, but makes it possible to set
 * GEOSERVER_WMS_URL externally. 
 */
public class GeoServerConfiguration extends GetCapabilitiesConfiguration {

    public static String GEOSERVER_WMS_URL = "GEOSERVER_WMS_URL";

    private static Log log = LogFactory.getLog(org.geowebcache.util.GeoServerConfiguration.class);
    
    public GeoServerConfiguration(CacheFactory cacheFactory,
            String mimeTypes, String metaTiling) {
        
        super(  cacheFactory, 
                GeoServerConfiguration.getWMSUrl(cacheFactory.getWebAppContext()),
                mimeTypes, 
                metaTiling);
    }
    
    private static String getWMSUrl(WebApplicationContext ctx) {
        String tmpUrl = ctx.getServletContext().getInitParameter(GEOSERVER_WMS_URL);
        if(tmpUrl != null && tmpUrl.length() > 7) {
            log.info("Using servlet init context parameter to configure "+GEOSERVER_WMS_URL+" to "+tmpUrl);
            return tmpUrl;
        }
        
        tmpUrl = System.getProperty(GEOSERVER_WMS_URL);
        if(tmpUrl != null && tmpUrl.length() > 7) {
            log.info("Using Java environment variable to configure "+GEOSERVER_WMS_URL+" to "+tmpUrl);
            return tmpUrl;
        }
        
        tmpUrl = System.getenv(GEOSERVER_WMS_URL);
        if(tmpUrl != null && tmpUrl.length() > 7) {
            log.info("Using System environment variable to configure "+GEOSERVER_WMS_URL+" to "+tmpUrl);
            return tmpUrl;
        }
                
        log.info("No context parameter, system or Java environment variables found for " + GEOSERVER_WMS_URL);
        log.info("Reverting to http://localhost:8080/geoserver");
        
        return "http://localhost:8080/geoserver";
    }
}
