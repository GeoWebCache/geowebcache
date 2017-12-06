package org.geowebcache.config;

import org.geowebcache.layer.TileLayer;

/**
 * Applies default values to a layer that has not been fully initialized.
 *
 */
public interface DefaultingConfiguration extends BaseConfiguration {

    /**
     * Configuration objects lacking their own defaults can delegate to this
     * @param layer
     */
    void setDefaultValues(TileLayer layer);

}