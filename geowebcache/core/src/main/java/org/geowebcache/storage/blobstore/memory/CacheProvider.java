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

import java.util.List;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration.EvictionPolicy;

/**
 * Interface providing access to a cache object. It must be used by the {@link MemoryBlobStore} class for caching
 * {@link TileObject} instances. This cache should be configured only with the setConfiguration method and should be
 * modified by calling in sequence: resetCache() and setConfiguration(). Users should be able to add and remove Layers
 * that should not be cached.
 *
 * @author Nicola Lagomarsini GeoSolutions
 */
public interface CacheProvider {

    /** Returns the {@link TileObject} for the selected id */
    public TileObject getTileObj(TileObject obj);

    /** Insert a {@link TileObject} in cache. */
    public void putTileObj(TileObject obj);

    /** Removes a {@link TileObject} from cache. */
    public void removeTileObj(TileObject obj);

    /** Removes all the {@link TileObject}s for the related layer from cache. */
    public void removeLayer(String layername);

    /** Removes all the cached {@link TileObject}s */
    public void clear();

    /** Resets the Cache status and requires to reconfigure it by calling setConfiguration(), */
    public void reset();

    /** Returns a {@link CacheStatistics} object containing the current cache statistics. */
    public CacheStatistics getStatistics();

    /** Sets the CacheConfiguration to use */
    void configure(CacheConfiguration configuration);

    /** Add a new Layer that should not be cached */
    public void addUncachedLayer(String layername);

    /** Remove a Layer so that it can be cached again */
    public void removeUncachedLayer(String layername);

    /**
     * Checks if the Layer must be cached or not
     *
     * @return true if the Layer should not be cached
     */
    public boolean containsUncachedLayer(String layername);

    /**
     * Returns a list of the supported {@link EvictionPolicy} of the cache Provider
     *
     * @return a list containing the supported eviction policy
     */
    public List<EvictionPolicy> getSupportedPolicies();

    /**
     * Indicates if the CacheProvider configuration can be changed.
     *
     * @return a boolean indicating if the cache configuration can be changed or not
     */
    public boolean isImmutable();

    /**
     * Indicates if this {@link CacheProvider} object can be used
     *
     * @return a boolean indicating that the {@link CacheProvider} can be used for caching
     */
    public boolean isAvailable();

    /**
     * Name of the {@link CacheProvider}
     *
     * @return a String with the Cache Provider name
     */
    public String getName();
}
