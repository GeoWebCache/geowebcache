package org.geowebcache.config;

/**
 * A specific unit of configurable information such as a GridSet or Layer.
 * @author smithkm
 *
 */
public interface Info {

    /**
     * Get the unique identifier (name) of the configuration element
     *
     * @return the name
     */
    String getName();
    
}
