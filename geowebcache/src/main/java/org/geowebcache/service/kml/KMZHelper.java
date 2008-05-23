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
 */
package org.geowebcache.service.kml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileResponse;

/**
 * Just a helper class for KMZ experimentation stuff
 * 
 * 
 * @author ak
 */
public class KMZHelper {
    
    private static Log log = 
        LogFactory.getLog(org.geowebcache.service.kml.KMZHelper.class);
    
    /**
     * Filters the given gridlocation 
     * 
     *  Note that this does an actual reques to the WMS backend and then
     *  throws the result way. Some may consider this a bit wasteful ;)
     *
     * @param tileLayer
     * @param srsIdx
     * @param formatStr
     * @param linkGridLocs
     * @return
     */
    public static int[][] filterGridLocs(TileLayer tileLayer, int srsIdx,
            String formatStr, int[][] linkGridLocs) {
        
        for(int i=0;i<linkGridLocs.length; i++) {
            if(linkGridLocs[i][2] > 0) {
                
                TileResponse tr = null;
                try {
                    tr = 
                    tileLayer.doNonMetatilingRequest(
                            linkGridLocs[i], srsIdx, formatStr);
                    
                } catch (GeoWebCacheException gwce) {
                    log.error(gwce.getMessage());
                    linkGridLocs[i][2] = -1;
                }
                
                // TODO remove length check
                if(tr == null || tr.status == 204 || tr.data.length < 200 ) {
                    //System.out.println("Jeeeeeez.. I'm sorry. That just wont cut it.");
                    linkGridLocs[i][2] = -1;
                } else {
                    //System.out.println("Good enough for government work..." + new String(tr.data));
                }
            }
        }
        
        return linkGridLocs;
    }
        
}
