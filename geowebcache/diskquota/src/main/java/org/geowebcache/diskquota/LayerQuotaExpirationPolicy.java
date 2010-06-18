package org.geowebcache.diskquota;

import java.io.File;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;

public interface LayerQuotaExpirationPolicy {

    /**
     * Returns the unique name for this expiration policy
     * <p>
     * This name is an identifier for the policy implemented by this strategy, not for each instance
     * of the concrete subclass. It can be used to match a policy in a collection.
     * </p>
     * 
     * @return an identifier for this quota expiration policy strategy
     */
    String getName();

    /**
     * Registers a layer's quota to be managed by this expiration policy
     * 
     * @param tileLayer
     * @param layerQuota
     */
    void attach(TileLayer tileLayer, LayerQuota layerQuota);

    void dettach(String layerName);

    void expireTiles(String layerName) throws GeoWebCacheException;

    void save(LayerQuota lq);

    /**
     * Makes sure stats information for the given (existing) tile exists
     * 
     * @param layerQuota
     * @param gridSetId
     * @param tileXYZ
     * @param file
     */
    void createInfoFor(LayerQuota layerQuota, String gridSetId, long[] tileXYZ, File file);

}
