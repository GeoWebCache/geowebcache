package org.geowebcache.storage;

public interface StorageBrokerListener {

    /**
     * Invoked when {@link StorageBroker#delete(String)} succeeded in deleting the layer given by
     * {@code layerName}
     */
    void layerDeleted(String layerName);

    /**
     * Invoked when {@link StorageBroker#delete(TileRange)} succeeded in deleting the tile range
     */
    void tileRangeDeleted(TileRange tileRange);

    /**
     * Invoked when {@link StorageBroker#expire(TileRange)} succeeded in expiring the tile range
     */
    void tileRangeExpired(TileRange tileRange);

    /**
     * Invoked when {@link StorageBroker#get(TileObject)} found the requested tile in the cache
     */
    void cacheHit(TileObject tileObj);

    /**
     * Invoked when {@link StorageBroker#get(TileObject)} did not produce a cache hit
     */
    void cacheMiss(TileObject tileObj);

    /**
     * Invoked when a call to {@link StorageBroker#put(TileObject)} method succeeded
     */
    void tileCached(TileObject tileObj);

    /**
     * Invoked when the {@link StorageBroker#destroy()} method is called, right before it returns
     * and once it's shut down
     */
    void shutDown();

}
