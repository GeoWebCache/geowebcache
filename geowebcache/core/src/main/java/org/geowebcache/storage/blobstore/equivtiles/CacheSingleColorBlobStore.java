/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Dana Lambert, Catalyst IT Ltd NZ, Copyright 2020
 */
package org.geowebcache.storage.blobstore.equivtiles;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.*;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * This class is an implementation of the {@link BlobStore} interface wrapping another {@link
 * BlobStore} implementation and supporting in single color tile storage.
 */
public class CacheSingleColorBlobStore
        implements BlobStoreSingleColorTiles, ApplicationContextAware {

    ApplicationContext applicationContext;

    /** {@link Log} object used for logging exceptions */
    private static final Log log = LogFactory.getLog(CacheSingleColorBlobStore.class);

    /** {@link BlobStore} to use when no element is found */
    private BlobStoreSingleColorTiles store;

    // Initial default capacity of ConcurrentHashMap is 16 but the JVM will resize if needed.
    private ConcurrentHashMap<String, String> referenceHashMap = new ConcurrentHashMap<>();
    private Set<String> cachedSimpleTiles = referenceHashMap.newKeySet();

    /** Setter for the store to wrap */
    public void setStore(BlobStoreSingleColorTiles store) {
        log.debug("Setting the wrapped store");

        if (store == null) {
            throw new NullPointerException("Input BlobStore cannot be null");
        }
        this.store = store;
    }

    /** @return The wrapped {@link BlobStore} implementation */
    public BlobStoreSingleColorTiles getStore() {
        log.debug("Returning the wrapped store");
        return store;
    }

    /**
     * Check if the image tile consists of a single color.
     *
     * @param image The buffered image to check.
     * @return whether the image consists of only one color.
     */
    private Boolean isSingleColorTile(BufferedImage image) {
        int firstPixelColor = image.getRGB(0, 0);
        Boolean sameColorTile = true;
        int w = image.getWidth();
        int h = image.getHeight();

        // Iterate over all pixels & and compare to saved color value
        outer:
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int pixel = image.getRGB(j, i);
                if (pixel != firstPixelColor) {
                    sameColorTile = false;
                    break outer;
                }
            }
        }

        return sameColorTile;
    }

    /**
     * Creates a special path to store single color tiles.
     *
     * @param tile The tile object.
     * @return the modified path if a single color tile, otherwise null.
     * @throws IOException if there is any problem with the tile input stream.
     */
    private String getSingleColorRef(TileObject tile) throws IOException {
        Resource blob = tile.getBlob();
        String mimeType = "/";

        try {
            mimeType = MimeType.createFromFormat(tile.getBlobFormat()).getMimeType();
        } catch (MimeException e) {
            log.warn("Could not determine mimetype for " + tile.toString());
        }

        String singleColorRef = null;
        String[] mimeTypeInfo = mimeType.split("/");
        String extension = mimeTypeInfo[1];

        // Read image data and see if it is a single color tile - takes too long but code works
        String blobType = mimeTypeInfo[0];
        if (blobType.equals("image")) {
            // Read tile and save the first pixel color value
            try (InputStream stream = blob.getInputStream()) {
                BufferedImage image = ImageIO.read(stream);

                if (isSingleColorTile(image)) {
                    singleColorRef = String.format("%s.%s", image.getRGB(0, 0), extension);
                }
            }
        }

        return singleColorRef;
    }

    @Override
    public void put(TileObject obj) throws StorageException {
        try {
            // Gets the color of the single color tile as a reference
            // If the tile is not single colour it will return null
            String singleColourRef = getSingleColorRef(obj);

            // If the tile is a single colour
            if (singleColourRef != null) {
                // If the single colour tile is not stored
                if (!cachedSimpleTiles.contains(singleColourRef)
                        && store.tileExistsInStorage(obj, singleColourRef)) {
                    // Upload with single colour ref
                    store.saveSingleColourTile(obj, singleColourRef);
                    cachedSimpleTiles.add(singleColourRef);
                }
                // Upload symlink
                store.putSymlink(obj, singleColourRef);
            } else {
                // Upload as normal
                store.put(obj);
            }
        } catch (IOException e) {
            throw new StorageException("Error uploading tile.");
        }
    }

    @Override
    public boolean layerExists(String layerName) {
        return store.layerExists(layerName);
    }

    @Override
    public void putSymlink(TileObject tileObject, String singleColourTileRef) {
        store.putSymlink(tileObject, singleColourTileRef);
    }

    @Override
    public boolean tileExistsInStorage(TileObject tile, String singleColourTileRef) {
        return store.tileExistsInStorage(tile, singleColourTileRef);
    }

    @Override
    public void saveSingleColourTile(TileObject tile, String singleColourTileRef)
            throws IOException {
        store.saveSingleColourTile(tile, singleColourTileRef);
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        return store.delete(layerName);
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        return store.deleteByGridsetId(layerName, gridSetId);
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        return store.delete(obj);
    }

    @Override
    public boolean delete(TileRange obj) throws StorageException {
        return store.delete(obj);
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        return store.get(obj);
    }

    @Override
    public void clear() throws StorageException {
        store.clear();
    }

    @Override
    public void destroy() {
        store.destroy();
    }

    @Override
    public void addListener(BlobStoreListener listener) {
        store.addListener(listener);
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return store.removeListener(listener);
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        return store.rename(oldLayerName, newLayerName);
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        return store.getLayerMetadata(layerName, key);
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        store.putLayerMetadata(layerName, key, value);
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId)
            throws StorageException {
        return store.deleteByParametersId(layerName, parametersId);
    }

    @Override
    public Set<Map<String, String>> getParameters(String layerName) throws StorageException {
        return store.getParameters(layerName);
    }

    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        return store.getParametersMapping(layerName);
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext)
            throws BeansException {
        this.applicationContext = applicationContext;
    }
}
