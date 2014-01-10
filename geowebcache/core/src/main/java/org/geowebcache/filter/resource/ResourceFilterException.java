package org.geowebcache.filter.resource;

import org.geowebcache.GeoWebCacheException;

public class ResourceFilterException extends GeoWebCacheException {

    /** serialVersionUID */
    private static final long serialVersionUID = -2028532864544316254L;

    public ResourceFilterException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public ResourceFilterException(String msg) {
        super(msg);
    }

    public ResourceFilterException(Throwable thrw) {
        super("Error applying BlobFilter",thrw);
    }
    
}
