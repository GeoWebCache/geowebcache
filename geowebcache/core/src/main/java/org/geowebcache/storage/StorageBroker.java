package org.geowebcache.storage;

import java.util.Map;
import java.util.Set;

import org.geowebcache.layer.TileLayer;

/**
 * Abstracts and manages the storing of cachable objects and their metadata.
 */
public interface StorageBroker {

    public abstract void addBlobStoreListener(BlobStoreListener listener);

    public abstract boolean removeBlobStoreListener(BlobStoreListener listener);

    /**
     * Completely eliminates the cache for the given layer.
     */
    public abstract boolean delete(String layerName) throws StorageException;

    /**
     * Completely deletes the cache for a layer/gridset combination; differs from truncate that the
     * layer doesn't need to have a gridSubset associated for the given gridset at runtime (in order
     * to handle the deletion of a layer's gridsubset)
     * 
     * @param layerName
     * @param removedGridset
     * @throws StorageException
     */
    public abstract boolean deleteByGridSetId(String layerName, String gridSetId)
            throws StorageException;
    
    /**
     * Completely deletes the cache for a layer/parameters combination
     * 
     * @param layerName
     * @param removedGridset
     * @throws StorageException
     */
    public abstract boolean deleteByParametersId(String layerName, String parametersId)
            throws StorageException;
    
    /**
     * Completely deletes the cache for a layer/parameters combination
     * 
     * @param layerName
     * @param removedGridset
     * @throws StorageException
     */
    public abstract boolean deleteByParameters(String layerName, Map<String,String> parameters)
            throws StorageException;

    public abstract boolean rename(String oldLayerName, String newLayerName)
            throws StorageException;

    public abstract boolean delete(TileRange trObj) throws StorageException;

    /**
     * Sets the Resource for the given TileObject from storage
     * @param tileObj TileOpject to set the Resource of
     * @return true if successful, false otherwise
     * @throws StorageException
     */
    public abstract boolean get(TileObject tileObj) throws StorageException;

    /**
     * Puts the given TileObject into storage
     * @param tileObj
     * @return
     * @throws StorageException
     */
    public abstract boolean put(TileObject tileObj) throws StorageException;

    /**
     * Destroy method for Spring
     */
    public abstract void destroy();

    public abstract String getLayerMetadata(String layerName, String key);

    public abstract void putLayerMetadata(String layerName, String key, String value);

    public abstract boolean getTransient(TileObject tile);

    public abstract void putTransient(TileObject tile);
    
    public abstract Set<String> getCachedParameterIds(String layerName) throws StorageException;
    
    public abstract Set<Map<String, String>> getCachedParameters(String layerName) throws StorageException;

    /**
     * Purge parameter caches from the layer if they are unreachable by its current parameter 
     * filters.  The store may purge gridsets and formats as well.  These additional purges may be 
     * guaranteed in future.
     * @param layer
     * @return
     * @throws StorageException
     */
    public abstract boolean purgeOrphans(final TileLayer layer) throws StorageException;

}