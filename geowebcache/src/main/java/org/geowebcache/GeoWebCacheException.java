package org.geowebcache;

public class GeoWebCacheException extends Exception {
    public GeoWebCacheException(String msg) {
        super(msg);
    }

    public GeoWebCacheException(Throwable thrw) {
        super(thrw);
    }
}
