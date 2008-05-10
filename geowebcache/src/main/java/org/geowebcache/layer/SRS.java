package org.geowebcache.layer;

import org.geowebcache.GeoWebCacheException;

public class SRS {
	private int number = -1;
	
	public SRS(String srs) throws GeoWebCacheException {
		if(srs.substring(0, 5).equalsIgnoreCase("EPSG:")) {
			number = Integer.parseInt(srs.substring(5, srs.length()));
		} else {
			throw new GeoWebCacheException("Can't parse " + srs);
		}
	}
	
	public SRS(int epsgNumber) {
		number = epsgNumber;
	}
	
	public boolean equals(Object other) {
            if(other == null || other.getClass() != this.getClass()){
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
		return "EPSG:"+Integer.toString(number);
	}
        
        public String filePath() {
                return "EPSG_"+Integer.toString(number);
        }
		
}
