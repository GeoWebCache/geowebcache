package org.geowebcache.seed;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;

/**
 * Represents a requests to truncate multiple gridsets or layers at once
 * @author Kevin Smith, OpenGeo
 *
 */
public interface MassTruncateRequest {

    /**
     * Perform the requested truncation
     * 
     * @param sb The storage broker managing the cache
     * @param config The configuration storing information about the affected layers
     * @return {@literal true} if successful, {@literal false} otherwise
     * @throws StorageException
     */
    public boolean doTruncate(StorageBroker sb, TileBreeder breeder) throws StorageException, GeoWebCacheException;
}
