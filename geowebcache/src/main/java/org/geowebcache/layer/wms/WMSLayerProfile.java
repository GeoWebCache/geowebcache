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
 * @author Arne Kepp, The Open Planning Project, Copyright 2007
 *  
 */
package org.geowebcache.layer.wms;

import java.net.URLConnection;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.service.wms.WMSParameters;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;
import org.geowebcache.util.wms.GridCalculator;

public class WMSLayerProfile {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.wms.WMSLayerProfile.class);

    public static final String WMS_URL = "wmsurl";

    public static final String WMS_SRS = "srs";

    public static final String WMS_BBOX = "bbox";
    
    public static final String WMS_STYLES = "wmsstyles";

    public static final String WMS_METATILING = "metatiling";

    public static final String WMS_TRANSPARENT = "transparent";
    
    public static final String WMS_VENDOR_PARAMS = "vendorparameters";

    public static final int CACHE_NEVER = 0;

    public static final int CACHE_VALUE_UNSET = -1;

    public static final int CACHE_NEVER_EXPIRE = -2;

    public static final int CACHE_USE_WMS_BACKEND_VALUE = -4;

 

    

    protected int width = 256;

    protected int height = 256;




    

    protected String version = "1.1.1";

    protected String errorMime = "application/vnd.ogc.se_inimage";

    protected String transparent = null;

    protected String tiled = null;



    

    protected String wmsLayers = "topp:states";



    protected WMSParameters wmsparams = null;
    
    protected boolean saveExpirationHeaders = false;

    protected long expireClients = CACHE_USE_WMS_BACKEND_VALUE;

    protected long expireCache = CACHE_NEVER_EXPIRE;




    

   
    
   
}