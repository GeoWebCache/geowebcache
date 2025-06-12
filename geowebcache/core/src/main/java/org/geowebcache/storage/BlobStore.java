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
 * @author Arne Kepp / The Open Planning Project 2009
 */
package org.geowebcache.storage;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.geotools.util.logging.Logging;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.layer.TileLayer;

/**
 * Manages the persistence of the actual data contained in cacheable objects (tiles, WFS responses).
 *
 * <p>Blobstores may assume that the StorageObjects passed to them are completely filled in except for the blob fields.
 */
public interface BlobStore {
    static Logger log = Logging.getLogger(BlobStore.class.getName());

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
     * @return {@literal true} if successful, {@literal false} otherwise
     */
    public boolean deleteByGridsetId(final String layerName, final String gridSetId) throws StorageException;

    /**
     * Delete the cache for the named layer and parameters.
     *
     * @param parameters Complete filtered parameters to generate the ID
     * @return {@literal true} if successful, {@literal false} otherwise
     */
    public default boolean deleteByParameters(final String layerName, final Map<String, String> parameters)
            throws StorageException {
        return deleteByParametersId(layerName, ParametersUtils.getId(parameters));
    }

    /**
     * Delete the cache for the named layer and parameters id.
     *
     * @return {@literal true} if successful, {@literal false} otherwise
     */
    public boolean deleteByParametersId(final String layerName, String parametersId) throws StorageException;

    /**
     * Delete the cached blob associated with the specified TileObject. The passed in object itself will not be
     * modified.
     *
     * @return {@literal true} if successful, {@literal false} otherwise
     */
    public boolean delete(TileObject obj) throws StorageException;

    /**
     * Delete the cached blob associated with the tiles in the given range.
     *
     * @param obj the range of tiles.
     * @return {@literal true} if successful, {@literal false} otherwise
     */
    public boolean delete(TileRange obj) throws StorageException;

    /**
     * Retrieves a tile from the storage, filling its metadata too
     *
     * @return {@literal true} if successful, {@literal false} otherwise
     */
    public boolean get(TileObject obj) throws StorageException;

    /** Store blob. Calls getBlob() on passed object, does not modify the object. */
    public void put(TileObject obj) throws StorageException;

    /** Wipes the entire storage. Should only be invoked during testing. */
    public void clear() throws StorageException;

    /** Destroy method for Spring */
    public void destroy();

    /**
     * Add an event listener
     *
     * @see BlobStoreListener
     */
    public void addListener(BlobStoreListener listener);

    /**
     * Remove an event listener
     *
     * @return {@literal true} if successful, {@literal false} otherwise
     * @see BlobStoreListener
     */
    public boolean removeListener(BlobStoreListener listener);

    /** Get the cached parameter maps for a layer */
    public default Set<Map<String, String>> getParameters(String layerName) throws StorageException {
        return getParametersMapping(layerName).values().stream()
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Get the IDs of the cached parameter maps for a layer.
     *
     * <p>Stores that predate 1.12 should implement this to provide any parameters for which maps are not available.
     * Stores not using {@link ParametersUtils#getId(Map)} or which have more efficient ways to provide it should also
     * implement it.
     */
    public default Set<String> getParameterIds(String layerName) throws StorageException {
        return getParametersMapping(layerName).keySet();
    }

    /**
     * Rename a stored layer
     *
     * @param oldLayerName the old name of the layer
     * @param newLayerName the new name
     * @return {@literal true} if successful or the layer didn't exist, {@literal false} otherwise
     * @throws StorageException if {@code newLayerName} already exists or the rename can't be accomplished for other
     *     reason
     */
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException;

    /** @return the value of the stored metadata for the given layer and key, or {@code null} if no such value exists */
    public String getLayerMetadata(String layerName, String key);

    /** Stores a metadata key/value pair for the given layer */
    public void putLayerMetadata(String layerName, String key, String value);

    /** @return {@code true} if the blobstore has a layer named {@code layerName} in use, {@code false} otherwise */
    public boolean layerExists(String layerName);

    /**
     * Gets the mapping from parameter IDs to parameter maps. For cache parameter IDs that lack reverse mappings (as
     * produced by GWC before 1.12) the ID will map to an empty {@link Optional}.
     *
     * @param layerName The layer to look up.
     * @return A map from parameter IDs to {@link Optional}s containing parameter maps, or empty {@link Optional}s.
     * @since 1.12
     */
    Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName);

    /** If the given layer is cached, remove */
    public default boolean purgeOrphans(TileLayer layer) throws StorageException {
        // TODO maybe do purging based on gridset and format
        try {
            final List<ParameterFilter> parameterFilters = layer.getParameterFilters();

            // Given known parameter mapping, figures out if the parameters need to be purged
            final Function<Map<String, String>, Boolean> parametersNeedPurge = parameters -> {
                return parameters.size() != parameterFilters.size()
                        || // Should have the same number of parameters as the layer has
                        // filters
                        parameterFilters.stream()
                                .allMatch(
                                        pfilter -> { // Do all the parameter filters on the
                                            // layer consider their parameter legal
                                            final String key = pfilter.getKey();
                                            final String value = parameters.get(key);
                                            if (Objects.isNull(value)) {
                                                return true; // No parameter for this filter
                                                // so purge
                                            }
                                            return !pfilter.isFilteredValue(value); // purge if it's not a filtered
                                            // value
                                        });
            };

            return getParametersMapping(layer.getName()).entrySet().stream()
                    .filter(parameterMapping -> {
                        return parameterMapping
                                .getValue()
                                .map(parametersNeedPurge)
                                .orElse(true); // Don't have the original values so purge
                    })
                    .map(Map.Entry::getKey) // The parameter id
                    .map(id -> {
                        try {
                            return this.deleteByParametersId(layer.getName(), id);
                        } catch (StorageException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .reduce((x, y) -> x || y) // OR results without short circuiting
                    .orElse(false);
        } catch (UncheckedIOException ex) {
            if (ex.getCause() instanceof StorageException) {
                throw (StorageException) ex.getCause();
            } else {
                throw ex;
            }
        }
    }

    // /**
    // * Test to see whether the blobstore is ready or not
    // */
    // public boolean isReady();
}
