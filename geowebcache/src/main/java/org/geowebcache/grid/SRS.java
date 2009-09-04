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

import java.util.Hashtable;

import org.geowebcache.GeoWebCacheException;

public class SRS implements Comparable {
    private final int number;

    private static final SRS EPSG4326 = new SRS(4326);

    private static final SRS EPSG3857 = new SRS(3857);
    
    private static final SRS EPSG900913 = new SRS(900913);
    
    private static Hashtable<Integer,SRS> list = new Hashtable<Integer,SRS>();

    private SRS(int epsgNumber) {
        number = epsgNumber;
    }
    
    
    /**
     * This is already externally synchronized
     *  
     * @param epsgNumber
     * @return
     */
    public static SRS getSRS(int epsgNumber) {
        SRS ret = list.get(epsgNumber);
        
        if(ret == null) {
            // We'll use these a lot, so leave some shortcuts that avoid all the hashing
            if(epsgNumber == 4326) {
                list.put(4326, EPSG4326);
            } else if(epsgNumber == 900913 || epsgNumber == 3785) {
                list.put(3785, EPSG3857);
            }
            
            ret = new SRS(epsgNumber);
            list.put(epsgNumber, ret);
        }

        return ret;
    }
    
    public static SRS getSRS(String epsgStr) throws GeoWebCacheException {
        if (epsgStr.substring(0, 5).equalsIgnoreCase("EPSG:")) {
            int epsgNumber = Integer.parseInt(epsgStr.substring(5, epsgStr.length()));
            return getSRS(epsgNumber);
        } else {
            throw new GeoWebCacheException("Can't parse " + epsgStr + " as SRS string.");
        }
    }
    
    public boolean equals(Object obj) {
        if(obj instanceof SRS) {
            SRS other = (SRS) obj;
            if(other.number == this.number
                    || (this.number == 3857 && other.number == 900913)
                    || (this.number == 900913 && other.number == 3857)) {
                return true;
            }
        }
        return false;
    }

    public int getNumber() {
        return number;
    }

    public int hashCode() {
        return number;
    }

    public String toString() {
        return "EPSG:" + Integer.toString(number);
    }

    public String filePath() {
        return "EPSG_" + Integer.toString(number);
    }

    public static SRS getEPSG4326() {
        return EPSG4326;
    }

    public static SRS getEPSG3857() {
        return EPSG3857;
    }
    
    public static SRS getEPSG900913() {
        return EPSG900913;
    }
    
    public int compareTo(Object o) {
        SRS other = (SRS) o;
        
        if(other.number == number) {
            return 0;
        } else if(other.number > number) {
            return -1;
        } else {
            return 1;
        }
        
    }
}
