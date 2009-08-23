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

import java.util.Arrays;

public class Grid {    
    // maxX and maxY
    protected long[] extent = new long[2];
    
    protected double resolution;
    
    protected double scale;
 
    protected String name;
    
    public boolean equals(Object obj) {
        if(! (obj instanceof Grid))
            return false;
        
        Grid other = (Grid) obj;
        
        if(! Arrays.equals(other.extent, extent))
            return false;
        
        if(other.resolution != resolution)
            return false;
        
        return true;
    }
    
    public long[] getExtent() {
        return extent;
    }
    
    public String getName() {
        return name;
    }
    
    public double getScale() {
        return scale;
    }
}
