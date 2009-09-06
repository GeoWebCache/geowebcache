package org.geowebcache.service;

import org.geowebcache.GeoWebCacheException;

public class ServiceException extends GeoWebCacheException {

	public ServiceException(Throwable thrw) {
		super(thrw);
	}
	
	public ServiceException(String message) {
		super(message);
	}

	private static final long serialVersionUID = 30867687291108387L;
	
}
