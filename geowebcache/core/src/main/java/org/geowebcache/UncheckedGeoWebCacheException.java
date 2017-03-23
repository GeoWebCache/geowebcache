package org.geowebcache;

import java.util.Objects;

public class UncheckedGeoWebCacheException extends RuntimeException {
    
    /** serialVersionUID */
    private static final long serialVersionUID = -7981050129260733945L;

    public UncheckedGeoWebCacheException(GeoWebCacheException cause) {
        super(cause);
        Objects.requireNonNull(cause);
    }
    
    @Override
    public synchronized GeoWebCacheException getCause() {
        return (GeoWebCacheException) super.getCause();
    }
}
