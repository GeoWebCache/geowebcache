/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp / The Open Planning Project 2009
 *  
 */
package org.geowebcache.storage;

/**
 * Manages the persistence of the actual data contained in cacheable objects (tiles, WFS responses).
 * <p>
 * Blobstores may assume that the StorageObjects passed to them are completely filled in except for
 * the blob fields.
 * </p>
 */
public interface BlobStore {
    /**
     * Delete the cache for the named layer
     * 
     * @param layerName the name of the layer to delete
     * @return {@literal true} if successful, {@literal false} otherwise
     */
    public boolean delete(String layerName) throws StorageException;

    /**
     * Delete the cache for the named gridset and layer
     * 
     * @param layerName
     * @param gridSetId
     * @return {@literal true} if successful, {@literal false} otherwise
     * @throws StorageException
     */
    public boolean deleteByGridsetId(final String layerName, final String gridSetId)
            throws StorageException;

    /**
     * Delete the cached blob associated with the specified TileObject.
     * The passed in object itself will not be modified.
     * 
     * @param obj
     * @return {@literal true} if successful, {@literal false} otherwise
     * @throws StorageException
     */
    public boolean delete(TileObject obj) throws StorageException;

    /**
     * Delete the cached blob associated with the tiles in the given range.
     * 
     * @param obj the range of tiles.
     * @return {@literal true} if successful, {@literal false} otherwise
     * @throws StorageException
     */
   public boolean delete(TileRange obj) throws StorageException;

    /**
     * Retrieves a tile from the storage, filling its metadata too
     * @param obj
     * @return {@literal true} if successful, {@literal false} otherwise
     * @throws StorageException
     */
    public boolean get(TileObject obj) throws StorageException;

    /**
     * Store blob. Calls getBlob() on passed object, does not modify the object.
     * 
     * @param key
     * @param blog
     * @throws StorageException
     */
    public void put(TileObject obj) throws StorageException;

    /**
     * Wipes the entire storage. Should only be invoked during testing.
     * 
     * @throws StorageException
     */
    public void clear() throws StorageException;

    /**
     * Destroy method for Spring
     */
    public void destroy();

    /**
     * Add an event listener 
     * @param listener
     * @see BlobStoreListener
     */
    public void addListener(BlobStoreListener listener);

    /**
     * Remove an event listener
     * @param listener
     * @return {@literal true} if successful, {@literal false} otherwise
     * @see BlobStoreListener
     */
    public boolean removeListener(BlobStoreListener listener);

    /**
     * Rename a stored layer
     * @param oldLayerName the old name of the layer
     * @param newLayerName the new name
     * @return {@literal true} if successful or the layer didn't exist, {@literal false} otherwise
     * @throws StorageException
     */
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException;

    /**
     * @return the value of the stored metadata for the given layer and key, or {@code null} if no
     *         such value exists
     */
    public String getLayerMetadata(String layerName, String key);

    /**
     * Stores a metadata key/value pair for the given layer
     */
    public void putLayerMetadata(String layerName, String key, String value);

    // /**
    // * Test to see whether the blobstore is ready or not
    // */
    // public boolean isReady();
}
