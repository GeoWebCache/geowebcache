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
 * <p>Copyright 2026
 */
package org.geowebcache.storage.memorycache;

import com.google.common.base.Ticker;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.geotools.util.logging.Logging;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.util.TMSKeyBuilder;

/**
 * In-memory tile cache shared by all cached layers of this node, built from one {@link MemoryCacheInfo}: its
 * {@link KeyValueStore}, guarded against outages. Thread safe.
 */
public class MemoryCache implements AutoCloseable {

    private static final Logger LOGGER = Logging.getLogger(MemoryCache.class.getName());

    private final MemoryCacheInfo info;

    /** Null when the cache is disabled, refusing every call when enabled but unusable. */
    private final GuardedStore store;

    private final TMSKeyBuilder keys;

    /** Whether the last parameter mapping write failed, so that a store outage is logged once, not per tile. */
    private final AtomicBoolean mappingWritesFailing = new AtomicBoolean();

    /** Whether the last tile delete ran on the blob store only, so that a cache outage is logged once, not per tile. */
    private final AtomicBoolean deletesSkippingCache = new AtomicBoolean();

    private final MemoryCacheStats stats;

    /**
     * Builds the cache for {@code info}, disabled when null. Invalid settings or a backend that cannot be created are
     * logged, not thrown, and leave the cache unusable: cached layers then miss and drop writes.
     */
    public MemoryCache(@Nullable MemoryCacheInfo info, TileLayerDispatcher layers) {
        this(info, layers, Ticker.systemTicker());
    }

    MemoryCache(@Nullable MemoryCacheInfo info, TileLayerDispatcher layers, Ticker ticker) {
        this.info = info;
        this.store = info != null && info.isEnabled() ? createStore(info, ticker) : null;
        // no key prefix, the cache holds tiles only
        this.keys = new TMSKeyBuilder(null, layers);
        this.stats = new MemoryCacheStats(info == null ? 0 : info.getStatsInterval(), ticker);
    }

    // never null: falling back to the layer's own store would write tiles there with passthrough off
    private static GuardedStore createStore(MemoryCacheInfo info, Ticker ticker) {
        if (info.getTimeToLive() < 1) {
            return GuardedStore.unusable("time to live must be at least 1 second, got " + info.getTimeToLive(), null);
        }
        KeyValueStoreInfo settings = info.getBackend();
        if (settings instanceof UnresolvedKeyValueStoreInfo unresolved) {
            return GuardedStore.unusable("backend " + unresolved.getAlias() + " is not installed", null);
        }
        try {
            return new GuardedStore(settings.createStore(), ticker);
        } catch (RuntimeException | LinkageError e) {
            // a LinkageError comes from a backend jar missing its library or built against another version of it;
            // left to escape, it would fail every blob store call, as the cache is built again on each of them
            return GuardedStore.unusable(
                    "could not create backend " + settings.getClass().getSimpleName(), e);
        }
    }

    /** The settings this cache was built from, null if none. */
    @Nullable
    public MemoryCacheInfo getInfo() {
        return info;
    }

    /**
     * Returns {@code store} unchanged when the cache is disabled, otherwise a store serving the layer tiles from the
     * cache if the layer uses it. Truncates clear the layer tiles from the cache either way.
     */
    public BlobStore wrap(TileLayer layer, BlobStore store) {
        return this.store == null ? store : new CachingBlobStore(store, this, layer, layer.isMemoryCached());
    }

    /**
     * Drops the tiles cached under the old name of a renamed layer, which a new layer with that name would serve
     * otherwise. Does nothing when layer ids are not their names, as in GeoServer. Never throws.
     */
    public void layerRenamed(String oldName, String newName) {
        if (store == null) return;
        try {
            // keys start with the layer id: one that survives the rename leaves nothing under the old name
            if (!newName.equals(keys.layerId(newName))) return;
        } catch (RuntimeException e) {
            // layer not found, remove to be safe
        }
        // the TMSKeyBuilder.forLayer layout, which cannot be used as no layer has that name any more
        String prefix = oldName + "/";
        try {
            store.removeByPrefix(prefix);
        } catch (RuntimeException e) {
            LOGGER.warning("In-memory tile cache could not remove the tiles of renamed layer " + oldName
                    + ", a new layer with that name may serve them until they expire");
        }
    }

    /** Closes the backend store; requests still running on it see failures, handled as misses. */
    @Override
    public void close() {
        if (store != null) store.close();
    }

    GuardedStore getStore() {
        return store;
    }

    long getTtlSeconds() {
        return info.getTimeToLive();
    }

    boolean isPassthrough() {
        return info.isPassthrough();
    }

    TMSKeyBuilder getKeys() {
        return keys;
    }

    AtomicBoolean getMappingWritesFailing() {
        return mappingWritesFailing;
    }

    AtomicBoolean getDeletesSkippingCache() {
        return deletesSkippingCache;
    }

    MemoryCacheStats getStats() {
        return stats;
    }
}
