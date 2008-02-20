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
package org.geowebcache.service.ve;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.BBOX;

/**
 * 
 */
public class VEConverter {
	private static Log log = LogFactory.getLog(org.geowebcache.service.ve.VEConverter.class);
	
	/**
	 * Convert a quadkey into a bounding box for EPSG:900913
	 * 
	 * @param quadKey
	 * @return
	 */
	public static BBOX convertQuadKey(String quadKey) {
		char[] quadArray = quadKey.toCharArray();
		
		int zoomLevel = quadArray.length;
		
		double extent = 20037508.34*2;
		
		// Start in the top left hand corner
		double xPos = -20037508.34;
		double yPos = 20037508.34;
		
		// Now we traverse the quadArray from left to right, interpretation
		//  0 1
		//  2 3
		// see http://msdn2.microsoft.com/en-us/library/bb259689.aspx
		//
		// What we'll end up with is the top left hand corner of the bbox
		//
		for(int i=0; i<zoomLevel; i++) {
			char curChar = quadArray[i];
			extent = extent/2; // For each round half as much is at stake
			
			if(curChar == '0') {
				// X,Y stay
			} else if(curChar == '1') {		
				xPos += extent;
				// Y stays
			} else if(curChar == '2') {
				// X stays
				yPos -= extent;	
			} else if(curChar == '3') {
				xPos += extent;
				yPos -= extent;
			} else {
				log.error("Don't know how to interpret quadKey: "+quadKey);
			}	
		}
		
		// xPos and yPos are the top left hand corner, extent is tilewidth
		return new BBOX(xPos, yPos - extent, xPos + extent, yPos);
	}
}
