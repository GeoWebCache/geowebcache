package org.geowebcache.config;

import org.geowebcache.layer.TileLayer;

/**
 * Applies default values to a layer that has not been fully initialized.
 *
 */
public interface DefaultingConfiguration extends BaseConfiguration {

    /**
     * TileLayerConfiguration objects lacking their own defaults can delegate to this.
     * Should set values in the default geowebcache.xml to the TileLayer configuration,
     * and fall back on implemented default values if missing.
     *
     * @param layer
     */
    void setDefaultValues(TileLayer layer);

}