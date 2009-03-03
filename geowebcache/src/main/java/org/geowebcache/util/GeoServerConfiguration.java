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

/**
 * This is just a subclass of the GetCapabilities object, but makes it possible to set
 * GEOSERVER_WMS_URL externally. 
 */
public class GeoServerConfiguration extends GetCapabilitiesConfiguration {

    public final static String GEOSERVER_WMS_URL = "GEOSERVER_WMS_URL";

    public final static String DEFAULT_GEOSERVER_WMS_URL = "http://localhost:8080/geoserver/wms?request=GetCapabilities";
    
    public final static String GEOWEBCACHE_VENDOR_PARAMS = "GEOWEBCACHE_VENDOR_PARAMS";
    
    
    //private static Log log = LogFactory.getLog(org.geowebcache.util.GeoServerConfiguration.class);
    
    public GeoServerConfiguration(ApplicationContextProvider ctxProv,
            String mimeTypes, String metaTiling) {
        super(  ctxProv.getSystemVar(GEOSERVER_WMS_URL, DEFAULT_GEOSERVER_WMS_URL),
                mimeTypes,
                metaTiling, 
                ctxProv.getSystemVar(GEOWEBCACHE_VENDOR_PARAMS,""),
                "true" );
    }    
    
    public GeoServerConfiguration(ApplicationContextProvider ctxProv, 
            String mimeTypes, String metaTiling, String vendorParams) {
        super(  ctxProv.getSystemVar(GEOSERVER_WMS_URL, DEFAULT_GEOSERVER_WMS_URL),
                mimeTypes, 
                metaTiling,
                vendorParams,
                "true" );
    }    
}
