package org.geowebcache.layer;

import org.geowebcache.GeoWebCacheException;

public class SRS {
    private int number = -1;

    private static transient final SRS epsg4326 = new SRS(4326);

    private static transient final SRS epsg900913 = new SRS(900913);

    public SRS(String srs) throws GeoWebCacheException {
        if (srs.substring(0, 5).equalsIgnoreCase("EPSG:")) {
            number = Integer.parseInt(srs.substring(5, srs.length()));
        } else {
            throw new GeoWebCacheException("Can't parse " + srs);
        }
    }

    public SRS(int epsgNumber) {
        number = epsgNumber;
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

}
