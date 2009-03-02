package org.geowebcache.layer;

import java.util.Hashtable;

import org.geowebcache.GeoWebCacheException;

public class SRS {
    private final int number;

    private static transient final SRS epsg4326 = new SRS(4326);

    private static transient final SRS epsg900913 = new SRS(900913);
    
    private static transient final SRS epsg3021 = new SRS(3021);

    private static transient final SRS epsg3006 = new SRS(3006);
    
    private static Hashtable<Integer,SRS> list = new Hashtable<Integer,SRS>();

    private SRS(int epsgNumber) {
        number = epsgNumber;
    }
    
    public static SRS getSRS(int epsgNumber) {
        SRS ret = list.get(epsgNumber);
        
        if(ret == null) {
            // We'll use these a lot, so leave some shortcuts that avoid all the hashing
            if(epsgNumber == 4326) {
                list.put(4326, SRS.getEPSG4326());
            } else if(epsgNumber == 900913) {
                list.put(900913, SRS.getEPSG900913());
            } else if(epsgNumber == 3021) {
                list.put(3021, SRS.getEPSG3021());
            } else if(epsgNumber == 3021) {
                list.put(3006, SRS.getEPSG3006());
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
    
    public boolean equals(Object other) {
        if (other == null || other.getClass() != this.getClass()) {
            return false;
        } else {
            SRS otherSRS = (SRS) other;
            return (otherSRS.number == this.number);
        }
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
        return epsg4326;
    }

    public static SRS getEPSG900913() {
        return epsg900913;
    }

    public static SRS getEPSG3021() {
        return epsg3021;
    }

    public static SRS getEPSG3006() {
        return epsg3006;
    }

}
