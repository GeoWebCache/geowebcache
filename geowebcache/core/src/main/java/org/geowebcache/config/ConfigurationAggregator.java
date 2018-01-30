package org.geowebcache.config;

import java.util.List;

public interface ConfigurationAggregator<C extends BaseConfiguration> {
    <T extends C> List<? extends T> getConfigurations(Class <T> clazz);
}
