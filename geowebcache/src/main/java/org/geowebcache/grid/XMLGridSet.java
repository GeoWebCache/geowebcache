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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.grid;

public class XMLGridSet {
    String name;
    
    SRS srs;
    
    BoundingBox extent;
    
    Boolean alignTopLeft;
    
    double[] resolutions;
    
    double[] scaleDenominators;
    
    Integer levels;
    
    Double metersPerUnit;
    
    String[] scaleNames;
    
    Integer tileHeight;
    
    Integer tileWidth;
    
    public String getName() {
        return name;
    }
    
    public GridSet makeGridSet() {
        if(tileWidth == null) {
            tileWidth = 256;
        }
        if(tileHeight == null) {
            tileHeight = 256;
        }
        
        if(alignTopLeft == null) {
            alignTopLeft = false;
        }
        
        if(resolutions != null || scaleDenominators != null) {
            return GridSetFactory.createGridSet(name, srs, extent, alignTopLeft, resolutions, scaleDenominators, metersPerUnit, scaleNames, tileWidth, tileHeight);
        } else {
            if(levels == null) {
                levels = 30;
            }
            
            return GridSetFactory.createGridSet(name, srs, extent, alignTopLeft, levels, metersPerUnit, tileWidth, tileHeight);
        }
    }
}