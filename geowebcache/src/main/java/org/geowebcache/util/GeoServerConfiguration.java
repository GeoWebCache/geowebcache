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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.context.WebApplicationContext;

/**
 * This is just a subclass of the GetCapabilities object, but makes it possible to set
 * GEOSERVER_WMS_URL externally. 
 */
public class GeoServerConfiguration extends GetCapabilitiesConfiguration {

    public final static String GEOSERVER_WMS_URL = "GEOSERVER_WMS_URL";

    public final static String GEOWEBCACHE_VENDOR_PARAMS = "GEOWEBCACHE_VENDOR_PARAMS";
    
    private static Log log = LogFactory.getLog(org.geowebcache.util.GeoServerConfiguration.class);
    
    public GeoServerConfiguration(ApplicationContextProvider ctxProv, String mimeTypes, String metaTiling) {
        super( GeoServerConfiguration.getSystemVar( ctxProv.getApplicationContext(), GEOSERVER_WMS_URL),
                        mimeTypes, 
                        metaTiling,
                        GeoServerConfiguration.getSystemVar( ctxProv.getApplicationContext(), GEOWEBCACHE_VENDOR_PARAMS),
                        "true"
            );
    }    
    
    public GeoServerConfiguration(ApplicationContextProvider ctxProv, String mimeTypes, String metaTiling, String vendorParams) {
        super( GeoServerConfiguration.getSystemVar(ctxProv.getApplicationContext(), GEOSERVER_WMS_URL),
                        mimeTypes, 
                        metaTiling,
                        vendorParams,
                        "true"
            );
    }  
    
    private static String getSystemVar(WebApplicationContext ctx, String varName) {
        String tmpVar = ctx.getServletContext().getInitParameter(varName);
        if(tmpVar != null && tmpVar.length() > 7) {
            log.info("Using servlet init context parameter to configure "+varName+" to "+tmpVar);
            return tmpVar;
        }
        
        tmpVar = System.getProperty(varName);
        if(tmpVar != null && tmpVar.length() > 7) {
            log.info("Using Java environment variable to configure "+varName+" to "+tmpVar);
            return tmpVar;
        }
        
        tmpVar = System.getenv(varName);
        if(tmpVar != null && tmpVar.length() > 7) {
            log.info("Using System environment variable to configure "+varName+" to "+tmpVar);
            return tmpVar;
        }
        
        tmpVar = "http://localhost:8080/geoserver/wms?request=GetCapabilities";
        log.info("No context parameter, system or Java environment variables found for " + varName);
        log.info("Reverting to " + tmpVar );
        
        return tmpVar;
    }    
}
