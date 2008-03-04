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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.BBOX;

/**
 * 
 */
public class GMapsConverter {
	private static Log log = LogFactory.getLog(org.geowebcache.service.gmaps.GMapsConverter.class);
	
	/**
	 * Convert Google's tiling coordinates into a bounding box for EPSG:900913
	 * 
	 * see http://code.google.com/apis/maps/documentation/overlays.html#Custom_Map_Types
	 * 
	 * @param quadKey
	 * @return
	 */
	public static BBOX convert(int zoomLevel, int x, int y) {
		double extent = 20037508.34*2;
		
		// Start in the top left hand corner
		double xPos = -20037508.34;
		double yPos = 20037508.34;
		
		double tileWidth = extent / (Math.pow(2, zoomLevel));
		
		// xPos and yPos are the top left hand corner, extent is tilewidth
		return new BBOX(
				xPos + x*tileWidth, 
				yPos - y*tileWidth,
				xPos + (x+1)*tileWidth, 
				yPos - (y+1)*tileWidth );
	}
}
