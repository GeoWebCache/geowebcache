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
package org.geowebcache.service.gmaps;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Class to convert from Google Maps coordinates into the internal
 * representation of a tile.
 */
public class GMapsConverter {
    private static Log log = LogFactory
    .getLog(org.geowebcache.service.gmaps.GMapsConverter.class);
    
    /**
     * Convert Google's tiling coordinates into an {x,y,x}
     * 
     * see
     * http://code.google.com/apis/maps/documentation/overlays.html#Custom_Map_Types
     * 
     * @param quadKey
     * @return
     */
    public static int[] convert(int zoomLevel, int x, int y, HttpServletResponse response) {
    	// Extent is the total number of tiles in y direction
    	int extent = (int) Math.pow(2, zoomLevel);
        
        if(x < 0 || x > extent - 1) {
            log.error("The X coordinate is not sane: " + x);
            return null;
        }
        
        if(y < 0 || y > extent - 1) {
            log.error("The Y coordinate is not sane: " + y);
            return null;
        }
        
        // xPos and yPos correspond to the top left hand corner
        int[] gridLoc = {x, extent - y - 1, zoomLevel};
        
        return gridLoc;
    }
}
