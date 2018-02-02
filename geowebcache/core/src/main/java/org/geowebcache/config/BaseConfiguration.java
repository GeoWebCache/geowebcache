package org.geowebcache.config;

import java.io.IOException;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;

public interface BaseConfiguration {

    public static final int BASE_PRIORITY = 0;
    
    /**
     * Initializes this configuration.
     * 
     * @throws GeoWebCacheException
     */
    void initialize() throws GeoWebCacheException;

    /**
     * @return non null identifier for this configuration
     */
    String getIdentifier();
    
    /**
     * The location is a string identifying where this configuration is persisted TileLayerConfiguration
     * implementations may choose whatever form is appropriate to their persistence mechanism and 
     * callers should not assume any particular format. In many but not all cases this will be a URL
     * or filesystem path.
     * 
     * @return Location string for this configuration
     */
    String getLocation();


    /**
     * Saves this configuration
     * 
     * @throws IOException
     * 
     * TODO get rid of this, 
     */
    void save() throws IOException;
    
    /**
     * Get the priority of this configuration when aggregating. Lower values will be used before 
     * higher ones.  This should always return the same value, for a given input.
     */
    default int getPriority(Class<? extends BaseConfiguration> clazz) {
        return BASE_PRIORITY;
    }

}