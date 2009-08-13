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

import java.util.Arrays;

/**
 * This is an annoying object that exists solely because Java doesn't play nice
 * with int[] in hashmaps
 * 
 * @author ak
 */
public class GridLocObj {    
    private long[] gridLoc;

    int hashCode;
        
    public GridLocObj(long[] gridLoc, int max) {
        this.gridLoc = gridLoc;
        this.hashCode = (int) (gridLoc[0] * 433 + gridLoc[1] * 19 + gridLoc[2]) % max;
    }

    public boolean equals(Object obj) {
        if (obj instanceof GridLocObj) {
            GridLocObj other = (GridLocObj) obj;
            return Arrays.equals(gridLoc, other.gridLoc);
        }
        
        return false;
    }

    public int hashCode() {
        return hashCode;
    }

    public String toString() {
        return "{" + gridLoc[0] + "," + gridLoc[1] + "," + gridLoc[2] + "}";
    }
}
