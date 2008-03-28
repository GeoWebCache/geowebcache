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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.service.Service;

/**
 * Class to convert from Virtual Earth quad keys to the internal
 * representation of a tile.
 */
public class VEConverter extends Service {
    public static final String SERVICE_VE = "/ve";
    
    private static Log log = LogFactory
            .getLog(org.geowebcache.service.ve.VEConverter.class);

    public VEConverter() {
        super(SERVICE_VE);
    }
    
    public String getLayerIdentifier(HttpServletRequest request) {
        return super.getLayersParameter(request);
    }
    
    /**
     * Convert a quadkey into the internal representation {x,y,z}
     * of a grid location
     * 
     * @param quadKey
     * @return internal representation
     */
    public static int[] convert(String quadKey, HttpServletResponse response) {
        char[] quadArray = quadKey.toCharArray();

        int zoomLevel = quadArray.length;

        int extent = (int) Math.pow(2, zoomLevel);
        int yPos = 0;
        int xPos = 0;
        
        // Now we traverse the quadArray from left to right, interpretation
        // 0 1
        // 2 3
        // see http://msdn2.microsoft.com/en-us/library/bb259689.aspx
        //
        // What we'll end up with is the top left hand corner of the bbox
        //
        for (int i = 0; i < zoomLevel; i++) {
            char curChar = quadArray[i];
            
            // For each round half as much is at stake
            extent = extent / 2;

            if (curChar == '0') {
                //X stays
                yPos += extent;
            } else if (curChar == '1') {
                xPos += extent;
                yPos += extent;
            } else if (curChar == '2') {
                //X stays
                //Y stays
            } else if (curChar == '3') {
                xPos += extent;
                //Y stays
            } else {
                log.error("Don't know how to interpret quadKey: " + quadKey);
                return null;
            }
        }
        
        int[] gridLoc = {xPos, yPos, zoomLevel};
        
        return gridLoc;
    }
}
