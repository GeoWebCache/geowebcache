package org.geowebcache.util;

import java.util.Map;

import org.geowebcache.GeoWebCacheException;

public interface Configuration {

	public Map getTileLayers() throws GeoWebCacheException;
	public String getIdentifier();
}
