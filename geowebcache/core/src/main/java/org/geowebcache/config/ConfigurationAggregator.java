package org.geowebcache.config;

import java.util.List;

/**
 * Interface for aggregators of {@link BaseConfiguration} implementations.
 * @see org.geowebcache.layer.TileLayerDispatcher
 * @see org.geowebcache.grid.GridSetBroker
 * @see org.geowebcache.storage.BlobStoreAggregator
 */
public interface ConfigurationAggregator<C extends BaseConfiguration> {
    <T extends C> List<? extends T> getConfigurations(Class <T> clazz);
}
