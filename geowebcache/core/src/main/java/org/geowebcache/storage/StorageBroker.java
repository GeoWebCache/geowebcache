package org.geowebcache.storage;

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

    public abstract boolean rename(String oldLayerName, String newLayerName)
            throws StorageException;

    public abstract boolean delete(TileRange trObj) throws StorageException;

    public abstract boolean get(TileObject tileObj) throws StorageException;

    public abstract boolean put(TileObject tileObj) throws StorageException;

    /**
     * Destroy method for Spring
     */
    public abstract void destroy();

    public abstract String getLayerMetadata(String layerName, String key);

    public abstract void putLayerMetadata(String layerName, String key, String value);

    public abstract boolean getTransient(TileObject tile);

    public abstract void putTransient(TileObject tile);

}