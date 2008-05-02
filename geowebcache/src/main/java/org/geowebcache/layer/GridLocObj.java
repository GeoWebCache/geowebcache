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
package org.geowebcache.layer;

/**
 * This is an annoying object that exists solely because Java doesn't play nice
 * with int[] in hashmaps
 * 
 * @author ak
 */
public class GridLocObj {
    private int x;
    private int y;
    private int z;
    
    public GridLocObj(int[] gridLoc) {
        x = gridLoc[0];
        y = gridLoc[1];
        z = gridLoc[2];   
    }
    
    public boolean equals(Object obj) {
        if(obj instanceof GridLocObj) {
            GridLocObj other = (GridLocObj) obj;
            return x == other.x && y == other.y && z == other.z;
        }
        
        return false;
    }
    
    public int hashCode() {
        return (int) ((x * 433 + y * 73 + z) % Integer.MAX_VALUE);
    }
    
    public String toString() {
        return "{"+x+","+y+","+z+"}";
    }
}
