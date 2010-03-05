package org.geowebcache.layer;

public class ExpirationRule {
    private int minZoom;
    
    private int expiration;
    
    public ExpirationRule(int minZoom, int expiration) {
        this.minZoom = minZoom;
        this.expiration = expiration;
    }

    public int getMinZoom() {
        return minZoom;
    }
    
    public int getExpiration() {
        return expiration;
    }
    
}
