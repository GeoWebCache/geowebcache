package org.geowebcache.diskquota;

import java.util.List;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;

public interface ExpirationPolicy {

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
     * Registers a layer's quota to be managed by this expiration policy, meaning this expiration
     * policy will listen to request events on the layer and maintain a persistent record of its
     * usage statistics in order to be able of expiring tiles for the layer when
     * {@link #expireTiles(String)} is called.
     * 
     * @param tileLayer
     *            the tile layer to attach to this tile expiration policy
     * @param layerQuota
     *            the disk usage quota to enforce for the layer when expiring tiles as a result of
     *            {@link #expireTiles(String)} being called.
     */
    void attach(TileLayer tileLayer, LayerQuota layerQuota);

    /**
     * Detaches the {@link TileLayer} given by {@code layerName} from this expiration policy,
     * meaning this policy will no longer listen to the layer's events and hence won't collect and
     * save usage statistics for it any more.
     * 
     * @param layerName
     *            the name of the layer to detach from this expiration policy
     * @return {@code true} if the layer was attached to this expiration policy at the time this
     *         method was called
     */
    boolean dettach(String layerName);

    /**
     * Expires tiles from the layer until it reaches its configured {@link LayerQuota#getQuota()
     * quota}
     * 
     * @param layerName
     * @throws GeoWebCacheException
     */
    void expireTiles(String layerName) throws GeoWebCacheException;

    void expireTiles(List<String> layerNames, Quota limit, Quota usedQuota)
            throws GeoWebCacheException;

    void save(String layerName);

    /**
     * Makes sure stats information for the given (existing) tile exists
     * 
     * @param layerQuota
     * @param gridSetId
     * @param x
     * @param y
     * @param z
     */
    void createTileInfo(LayerQuota layerQuota, String gridSetId, long x, long y, int z);

    /**
     * Removes any held information for the tile given by the {@code x, y, z} ordinates
     * 
     * @param layerQuota
     * @param gridSetId
     * @param x
     * @param y
     * @param z
     */
    void removeTileInfo(LayerQuota layerQuota, String gridSetId, long x, long y, int z);

}
