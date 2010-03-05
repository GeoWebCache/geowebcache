package org.geowebcache.grid;

import org.geowebcache.GeoWebCacheException;

public abstract class GridMismatchException extends GeoWebCacheException {

    public GridMismatchException(String msg) {
        super(msg);
    }

}
