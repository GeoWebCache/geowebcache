package org.geowebcache.rest.webresources;

import java.net.URL;
import java.util.function.Function;

/**
 * Represents a bundle of resources for the web UI
 * @author Kevin Smith
 *
 */
public interface WebResourceBundle extends Function<String, URL>{
}
