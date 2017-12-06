package org.geowebcache.config;

import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.locks.LockProvider;

public interface ServerConfiguration extends BaseConfiguration {
    ServiceInformation getServiceInformation();

    boolean isRuntimeStatsEnabled();

    LockProvider getLockProvider();

    /**
     * Used for getting the "fullWMS" parameter from GeoWebCacheConfigration
     * @return
     */
    Boolean getfullWMS();

}
