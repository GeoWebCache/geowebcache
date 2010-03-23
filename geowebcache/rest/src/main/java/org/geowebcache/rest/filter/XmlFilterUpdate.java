package org.geowebcache.rest.filter;

import java.io.IOException;

import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.RestletException;

public abstract class XmlFilterUpdate {
    abstract protected void runUpdate(RequestFilter filter, TileLayer tl) throws IOException, RestletException;
}
