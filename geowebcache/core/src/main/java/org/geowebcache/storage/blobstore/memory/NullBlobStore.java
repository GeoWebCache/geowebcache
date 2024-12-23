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
package org.geowebcache.storage.blobstore.memory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

/**
 * This class is an implementation of the {@link BlobStore} interface which does not store anything on the file system
 * and can be used for doing a pure in memory caching of the {@link TileObject}s. This class simply stores the layer
 * metadata in a {@link Map} object. The other operations are not executed.
 *
 * @author Nicola Lagomarsini Geosolutions
 */
public class NullBlobStore implements BlobStore {

    private Map<String, Properties> metadataMap;

    /** {@link Logger} object used for logging exceptions */
    private static final Logger LOGGER = Logging.getLogger(NullBlobStore.class.getName());

    public NullBlobStore() {
        // Map initialization
        metadataMap = new HashMap<>();
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        return true;
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        return true;
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        return true;
    }

    @Override
    public boolean delete(TileRange obj) throws StorageException {
        return true;
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        return false;
    }

    @Override
    public void put(TileObject obj) throws StorageException {}

    @Override
    public void clear() throws StorageException {}

    @Override
    public synchronized void destroy() {
        // Clear the properties map
        metadataMap.clear();
    }

    @Override
    public void addListener(BlobStoreListener listener) {}

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return true;
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        return true;
    }

    @Override
    public synchronized String getLayerMetadata(String layerName, String key) {
        // Check if the property is present
        Properties properties = metadataMap.get(layerName);
        if (properties != null) {
            // Returns the property associated to the key
            try {
                return URLDecoder.decode((String) properties.get(key), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
        }
        return null;
    }

    @Override
    public synchronized void putLayerMetadata(String layerName, String key, String value) {
        // Check if the property is present
        Properties props = metadataMap.get(layerName);
        if (props != null) {
            try {
                // If present adds the new property
                props.setProperty(key, URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
        } else {
            // Else creates a new Property object and them adds the new property
            props = new Properties();
            try {
                props.setProperty(key, URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
            metadataMap.put(layerName, props);
        }
    }

    @Override
    public boolean layerExists(String layerName) {
        return false;
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId) throws StorageException {
        return true;
    }

    @Override
    public Set<Map<String, String>> getParameters(String layerName) {
        return Collections.emptySet();
    }

    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        return Collections.emptyMap();
    }
}
