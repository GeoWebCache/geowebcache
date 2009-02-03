package org.geowebcache.util.wms;


public abstract class ExtentHandler {
	
	public ExtentHandler() {
	}
	
	public abstract boolean isValid(Dimension dimension, String value);
	
	public abstract Object getValue(String value);
	
}
