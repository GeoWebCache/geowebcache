package org.geowebcache.layer;

public class ExpirationRule {
    private int minZoom;
    
    private int expiration;
    
    /**
     * XStream needs a no-args constructor to make an instance of this class to load details into.
	 */
	public ExpirationRule() {
		;
	}
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
