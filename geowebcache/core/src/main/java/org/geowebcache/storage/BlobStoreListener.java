/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.storage;

import org.geowebcache.grid.GridSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;

/**
 * Listener interface for {@link BlobStore} events.
 *
 * <p>Implementations can be added to the blob store(s) through the
 * {@link StorageBroker#addBlobStoreListener(BlobStoreListener) StorageBroker}
 */
public interface BlobStoreListener {

    /**
     * Notifies that the blob store stored a new tile for the given parameters.
     *
     * @param layerName {@link TileLayer#getName() name} of the layer for the stored tile
     * @param gridSetId {@link GridSet#getName() name} of the gridset for the stored tile
     * @param blobFormat {@link MimeType#getFormat() format name} of the mime type for the stored tile
     * @param parametersId {@link TileObject#getParametersId() parameters id} for the stored tile
     * @param x X ordinate of the {@link TileObject#getXYZ() grid location} for the stored tile
     * @param y Y ordinate of the {@link TileObject#getXYZ() grid location} for the stored tile
     * @param z Z ordinate of the {@link TileObject#getXYZ() grid location} for the stored tile
     * @param blobSize the actual storage size the blob uses in the blob store's backend storage; which may or may not
     *     equal to the encoded tile size, depending on the storage mechanism
     */
    void tileStored(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize);

    /**
     * Notifies that the blob store has deleted a tile given by the method arguments.
     *
     * @param layerName {@link TileLayer#getName() name} of the layer for the stored tile
     * @param gridSetId {@link GridSet#getName() name} of the gridset for the stored tile
     * @param blobFormat {@link MimeType#getFormat() format name} of the mime type for the stored tile
     * @param parametersId {@link TileObject#getParametersId() parameters id} for the stored tile
     * @param x X ordinate of the {@link TileObject#getXYZ() grid location} for the stored tile
     * @param y Y ordinate of the {@link TileObject#getXYZ() grid location} for the stored tile
     * @param z Z ordinate of the {@link TileObject#getXYZ() grid location} for the stored tile
     * @param blobSize the actual blob size freed from the blob store's backend storage; which may or may not equal to
     *     the encoded tile size, depending on the storage mechanism
     */
    void tileDeleted(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize);

    /**
     * Notifies that the blob store replaced an existing tile blob by a new one.
     *
     * @param layerName {@link TileLayer#getName() name} of the layer for the stored tile
     * @param gridSetId {@link GridSet#getName() name} of the gridset for the stored tile
     * @param blobFormat {@link MimeType#getFormat() format name} of the mime type for the stored tile
     * @param parametersId {@link TileObject#getParametersId() parameters id} for the stored tile
     * @param x X ordinate of the {@link TileObject#getXYZ() grid location} for the stored tile
     * @param y Y ordinate of the {@link TileObject#getXYZ() grid location} for the stored tile
     * @param z Z ordinate of the {@link TileObject#getXYZ() grid location} for the stored tile
     * @param blobSize the actual storage size the blob uses in the blob store's backend storage; which may or may not
     *     equal to the encoded tile size, depending on the storage mechanism
     * @param oldSize the size of the replaced blob
     */
    void tileUpdated(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize,
            long oldSize);

    /** Notifies that the layer named {@code layerName} has been whipped out from the blob store's backend storage. */
    void layerDeleted(String layerName);

    /**
     * Notifies that the layer named {@code oldLayerName} has been renamed as {@code newLayerName} in the blob store's
     * backend storage.
     */
    void layerRenamed(String oldLayerName, String newLayerName);

    /**
     * Notifies that all tiles for the gridset {@code gridsetId} of layer {@code layerName} have been deleted in the
     * blob store's backend storage.
     */
    void gridSubsetDeleted(String layerName, String gridSetId);

    /**
     * Notifies that all tiles for the parameter ID {@code parametersId} of layer {@code layerName} have been deleted in
     * the blob store's backend storage.
     */
    void parametersDeleted(String layerName, String parametersId);
}
