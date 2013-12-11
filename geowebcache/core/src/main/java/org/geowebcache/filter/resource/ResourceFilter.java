package org.geowebcache.filter.resource;

import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeType;

/**
 * A filter which manipulates Resources as they are stored.
 * 
 * @author Kevin Smith, Boundless
 *
 */
public interface ResourceFilter {
    /**
     * Filter a resource before it is stored.
     * @param res
     */
    public void applyTo(Resource res, MimeType type) throws ResourceFilterException;
}
