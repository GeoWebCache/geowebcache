package org.geowebcache.layer;

public class TileRequest {
    public int[] gridLoc = null;
    public String mimeType = null;
    
    public TileRequest(int[] gridLoc, String mimeType) {
        this.gridLoc = gridLoc;
        this.mimeType = mimeType;
    }
    
}
