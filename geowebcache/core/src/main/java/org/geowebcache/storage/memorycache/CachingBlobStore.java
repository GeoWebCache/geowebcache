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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.geowebcache.storage.memorycache.MemoryCacheStats.Result.ERROR;
import static org.geowebcache.storage.memorycache.MemoryCacheStats.Result.EXPIRED;
import static org.geowebcache.storage.memorycache.MemoryCacheStats.Result.HIT;
import static org.geowebcache.storage.memorycache.MemoryCacheStats.Result.MISS;
import static org.geowebcache.storage.memorycache.MemoryCacheStats.Result.STORE;

import com.google.common.util.concurrent.Striped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.memorycache.MemoryCacheStats.LayerStats;
import org.geowebcache.storage.memorycache.MemoryCacheStats.Result;
import org.geowebcache.util.TMSKeyBuilder;

/**
 * Puts a {@link MemoryCache} in front of the layer blob store, which still keeps the layer metadata and parameter
 * mappings.
 *
 * <p>Cache failures never fail a tile request: a failed read is a miss, a failed write is dropped. A truncate of a
 * memory cached layer fails before the store while the cache is down or refuses the removal; a layer removal deletes
 * the store anyway, then throws.
 */
class CachingBlobStore implements BlobStore {

    private static final Logger LOGGER = Logging.getLogger(CachingBlobStore.class.getName());

    // value layout: format version, creation time in epoch millis, tile bytes
    private static final byte FORMAT_VERSION = 1;

    private static final int HEADER_SIZE = Byte.BYTES + Long.BYTES;

    /** Returned by {@link #cacheGet} when the cache failed, told apart from a miss by identity. */
    private static final byte[] FAILED = {};

    /**
     * Locks of the parameter mapping writes, by layer id and parameters id; static, so that a cache rebuilt by a
     * settings save and the one it replaces do not write the same mapping at once.
     */
    private static final Striped<Lock> MAPPING_LOCKS = Striped.lazyWeakLock(256);

    private final BlobStore delegate;

    private final MemoryCache cache;

    private final TMSKeyBuilder keys;

    private final TileLayer layer;

    /** Whether the layer's tiles go through the cache, false when only its bulk truncates do. */
    private final boolean cached;

    /** Null when not {@link #cached}. */
    private final LayerStats stats;

    /** Every tile operation must target {@code layer}. */
    CachingBlobStore(BlobStore delegate, MemoryCache cache, TileLayer layer, boolean cached) {
        this.delegate = delegate;
        this.cache = cache;
        this.keys = cache.getKeys();
        this.layer = layer;
        this.cached = cached;
        this.stats = cached ? cache.getStats().forLayer(layer.getName()) : null;
    }

    /** True when the store behind holds the tiles: passthrough on, or a layer not using the cache. */
    private boolean storeHoldsTiles() {
        return cache.isPassthrough() || !cached;
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        if (!cached) return delegate.get(obj);
        long start = System.nanoTime();
        String key = keys.forTile(obj, layer.getId());
        Result result = decode(obj, cacheGet(key));
        boolean found = result == HIT || result == EXPIRED;
        if (!found && cache.isPassthrough() && delegate.get(obj)) {
            // never over a newer tile a concurrent put cached meanwhile
            cachePut(key, obj, obj.getCreated(), true);
            found = true;
            // a failed cache read stays an error even when the store had the tile
            if (result == MISS) result = STORE;
        }
        stats.read(result);
        logRead(obj, result, start);
        cache.getStats().report();
        return found;
    }

    /** Fills {@code obj} from a cache value, which is null on a miss and {@link #FAILED} when the read failed. */
    private Result decode(TileObject obj, byte[] value) {
        if (value == FAILED) return ERROR;
        if (value == null || value.length < HEADER_SIZE || value[0] != FORMAT_VERSION) return MISS;
        long created = ByteBuffer.wrap(value, 1, Long.BYTES).getLong();
        obj.setCreated(created);
        int length = value.length - HEADER_SIZE;
        // the offset constructor rejects an empty tile
        obj.setBlob(
                length > 0 ? new ByteArrayResource(value, HEADER_SIZE, length) : new ByteArrayResource(new byte[0]));
        // the time to live already ends there, unless the writing node's clock is behind this one
        int expireCache = expireCache(obj);
        return expireCache > 0 && System.currentTimeMillis() - created > expireCache * 1000L ? EXPIRED : HIT;
    }

    private static void logRead(TileObject obj, Result result, long start) {
        long[] xyz = obj.getXYZ();
        LOGGER.fine(() -> String.format(
                Locale.ROOT,
                "In-memory tile cache %s for layer %s, gridset %s, z/x/y %d/%d/%d in %.2f ms",
                result.name().toLowerCase(Locale.ROOT),
                obj.getLayerName(),
                obj.getGridSetId(),
                xyz[2],
                xyz[0],
                xyz[1],
                (System.nanoTime() - start) / 1_000_000.0));
    }

    @Override
    public void put(TileObject obj) throws StorageException {
        if (!cached) {
            delegate.put(obj);
            return;
        }
        // first, as building the key also sets the parameters id the mapping write needs
        String key = keys.forTile(obj, layer.getId());
        if (cache.isPassthrough()) {
            delegate.put(obj);
        } else {
            putParametersMetadataOnce(obj, key);
        }
        cachePut(key, obj, System.currentTimeMillis(), false);
        cache.getStats().report();
    }

    /**
     * Writes the mapping the store behind would record on put, unless the cache holds a marker saying it is there.
     * Never throws: a failed write is retried by the next put of the parameter set.
     */
    private void putParametersMetadataOnce(TileObject obj, String tileKey) {
        String parametersId = obj.getParametersId();
        if (parametersId == null) return;
        // shared by every node: a truncate removing the mapping from the store removes the marker with the tiles
        String marker = markerKey(tileKey, parametersId);
        byte[] storeId = String.valueOf(layer.getBlobStoreId()).getBytes(UTF_8);
        Lock lock = MAPPING_LOCKS.get(layer.getId() + "/" + parametersId);
        // one check at a time: the tiles of a metatile are saved in parallel, and concurrent writers of one file make
        // it briefly disappear on Windows, where a rename deletes the target first; the marker is written before the
        // lock is released, so the threads waiting on it find the marker and return
        lock.lock();
        try {
            byte[] written = cacheGet(marker);
            // with the cache down the tile is not cached either, so it needs no mapping
            if (written == FAILED || Arrays.equals(written, storeId)) return;
            delegate.putParametersMetadata(obj.getLayerName(), parametersId, obj.getParameters());
            cacheWrite(marker, storeId, cache.getTtlSeconds(), false);
            if (cache.getMappingWritesFailing().compareAndSet(true, false)) {
                LOGGER.severe("In-memory tile cache records parameter mappings in the blob stores again");
            }
        } catch (StorageException | RuntimeException e) {
            // the tile is cached anyway: failing the put would render every request while the store is down
            if (cache.getMappingWritesFailing().compareAndSet(false, true)) {
                // SEVERE as the outage lines, production logging keeps org.geowebcache at error
                String message = "In-memory tile cache could not record parameters " + parametersId + " of layer "
                        + obj.getLayerName() + " in its blob store, retrying on the next tiles: until then truncates"
                        + " by parameters or bounding box miss the tiles cached meanwhile";
                LOGGER.log(Level.SEVERE, message, e);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * The marker of a parameter set mapping, {@code <layer>/<gridset>/<format>/<parameters>/mapping}: under the
     * prefixes of layer, gridset and parameters truncates, outside those of the zoom levels a bounding box truncate
     * removes.
     */
    private static String markerKey(String tileKey, String parametersId) {
        String segment = "/" + parametersId + "/";
        return tileKey.substring(0, tileKey.indexOf(segment) + segment.length()) + "mapping";
    }

    private byte[] cacheGet(String key) {
        try {
            return cache.getStore().get(key);
        } catch (RuntimeException e) {
            // logged by the guarded store, once per outage
            return FAILED;
        }
    }

    /**
     * Caches the tile. A read through from the store is capped at the global time to live: read before a concurrent
     * truncate, it can land after it, and must not be served for the layer's whole expire server cache.
     */
    private void cachePut(String key, TileObject obj, long created, boolean readThrough) {
        long ttl = ttlSeconds(obj, created);
        if (readThrough) ttl = Math.min(ttl, cache.getTtlSeconds());
        // a tile read through from the store can be already past the layer's expire server cache
        if (ttl <= 0) return;
        byte[] value;
        try {
            value = encode(obj.getBlob(), created);
        } catch (IOException e) {
            // a lazy store resource whose file a truncate removed after the read
            LOGGER.log(Level.FINE, "Could not read tile " + key + " to cache it", e);
            stats.failedPut();
            return;
        }
        if (!cacheWrite(key, value, ttl, readThrough)) stats.failedPut();
    }

    /** Writes a value, unless present when {@code ifAbsent}, returning false when the cache failed. */
    private boolean cacheWrite(String key, byte[] value, long ttl, boolean ifAbsent) {
        try {
            if (ifAbsent) cache.getStore().putIfAbsent(key, value, ttl);
            else cache.getStore().put(key, value, ttl);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // reads the stream rather than trusting the declared size, which a lazy resource computes apart from its content
    private static byte[] encode(Resource blob, long created) throws IOException {
        try (InputStream in = blob.getInputStream()) {
            byte[] tile = in.readAllBytes();
            return ByteBuffer.allocate(HEADER_SIZE + tile.length)
                    .put(FORMAT_VERSION)
                    .putLong(created)
                    .put(tile)
                    .array();
        }
    }

    /**
     * What is left of the layer's expire server cache since {@code created}, or the global time to live when the layer
     * sets none. Zero or negative when nothing is left.
     */
    private long ttlSeconds(TileObject obj, long created) {
        int expireCache = expireCache(obj);
        if (expireCache <= 0) return cache.getTtlSeconds();
        // not capped by the global time to live: a tile evicted before its expiry would only be rendered again
        return expireCache - MILLISECONDS.toSeconds(System.currentTimeMillis() - created);
    }

    /** The layer's expire server cache in seconds at the tile's zoom level, positive only when the layer sets one. */
    private int expireCache(TileObject obj) {
        // zero and negative values are "unset", "never expire" or "use the backend value"
        return layer.getExpireCache((int) obj.getXYZ()[2]);
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        String prefix = keys.forLayer(layerName);
        Consumer<KeyValueStore> removeFromCache = store -> store.removeByPrefix(prefix);
        StoreTruncate deleteStore = () -> delegate.delete(layerName);
        if (!truncateNeedsCache()) return truncate(removeFromCache, deleteStore);
        // only a layer removal deletes a whole layer, truncates go gridset by gridset: the layer is gone and nobody can
        // retry, so its store is deleted whatever the cache, whose leftovers expire with their time to live
        cacheRemovalFailure(removeFromCache);
        return truncateStoreThenCache(removeFromCache, deleteStore);
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        String prefix = keys.forGridset(layerName, gridSetId);
        return truncateTiles(
                store -> store.removeByPrefix(prefix), () -> delegate.deleteByGridsetId(layerName, gridSetId));
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId) throws StorageException {
        Set<String> prefixes = keys.forParameters(layerName, parametersId);
        return truncate(
                store -> prefixes.forEach(store::removeByPrefix),
                () -> delegate.deleteByParametersId(layerName, parametersId));
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        // disk quota deletes tile by tile, two cache calls each are not worth one stale tile of a former cached layer
        if (!cached) return delegate.delete(obj);
        String key = keys.forTile(obj, layer.getId());
        return deleteTiles(store -> store.remove(key), () -> delegate.delete(obj));
    }

    @Override
    public boolean delete(TileRange range) throws StorageException {
        // prefixes first, so that only store failures count as a cache outage
        List<String> prefixes = IntStream.rangeClosed(range.getZoomStart(), range.getZoomStop())
                .mapToObj(z -> keys.forZoomLevel(range, z))
                .toList();
        return deleteTiles(store -> removeRange(store, range, prefixes), () -> delegate.delete(range));
    }

    /**
     * A {@link #truncateTiles} that, with passthrough on, still deletes from the store behind while the cache is down,
     * so that disk quota frees space during an outage; the cached copies are then served until they expire.
     */
    private boolean deleteTiles(Consumer<KeyValueStore> removeFromCache, StoreTruncate truncateStore)
            throws StorageException {
        // with passthrough off the store holds no tiles, there is no space to free: fail as the other truncates
        if (!truncateNeedsCache() || !storeHoldsTiles()) return truncateTiles(removeFromCache, truncateStore);
        if (cacheRemovalFailure(removeFromCache) != null) {
            if (cache.getDeletesSkippingCache().compareAndSet(false, true)) {
                // SEVERE as the outage lines, production logging keeps org.geowebcache at error
                LOGGER.severe("In-memory tile cache is unreachable, tile deletes of layer " + layer.getName()
                        + " run on the blob store only, cached copies are served until they expire");
            }
            return truncateStore.run();
        }
        cache.getDeletesSkippingCache().set(false);
        return truncateStoreThenCache(removeFromCache, truncateStore);
    }

    /** Removes the range from the cache, {@code prefixes} holding the key prefix of each of its zoom levels. */
    private static void removeRange(KeyValueStore store, TileRange range, List<String> prefixes) {
        for (int i = 0; i < prefixes.size(); i++) {
            int z = range.getZoomStart() + i;
            store.removeMatching(prefixes.get(i), key -> inRange(range, key, z));
        }
    }

    /** Checks a key ending with {@code <x>/<y>.<extension>} against the range; a key of any other shape is in it. */
    private static boolean inRange(TileRange range, String key, int z) {
        int dot = key.lastIndexOf('.');
        int ySlash = key.lastIndexOf('/', dot);
        int xSlash = key.lastIndexOf('/', ySlash - 1);
        try {
            long x = Long.parseLong(key, xSlash + 1, ySlash, 10);
            long y = Long.parseLong(key, ySlash + 1, dot, 10);
            return range.contains(x, y, z);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            // removing more than asked is allowed, throwing would turn one stray key into a cache outage
            return true;
        }
    }

    @Override
    public void clear() throws StorageException {
        truncate(KeyValueStore::clear, () -> {
            delegate.clear();
            return true;
        });
    }

    /**
     * A {@link #truncate} of tiles only, leaving the store behind alone when it holds no tiles. Layer and parameters
     * truncates still reach it, since they also remove metadata and mappings.
     */
    private boolean truncateTiles(Consumer<KeyValueStore> removeFromCache, StoreTruncate truncateStore)
            throws StorageException {
        // with passthrough off nothing reads through, so the second removal protects nothing, kept to share one
        // failure path
        return truncate(removeFromCache, () -> !storeHoldsTiles() || truncateStore.run());
    }

    /**
     * Removes the truncated tiles from the cache, truncates the store behind, then removes them from the cache again.
     *
     * @param removeFromCache removes the keys of the truncated tiles, run twice
     * @param truncateStore the same truncate on the store behind, whose result is returned
     * @throws StorageException for a memory cached layer, before the store when the first removal fails or is refused,
     *     a retry then truncates both; after it when the second removal fails
     */
    private boolean truncate(Consumer<KeyValueStore> removeFromCache, StoreTruncate truncateStore)
            throws StorageException {
        if (!truncateNeedsCache()) {
            // only leftovers there, which nothing reads; refused at once while the cache is down
            cacheRemovalFailure(removeFromCache);
            return truncateStore.run();
        }
        // truncating the store alone would leave the cached tiles, served again once the cache is back
        RuntimeException failure = cacheRemovalFailure(removeFromCache);
        if (failure != null) {
            String message = "In-memory tile cache is unreachable, truncate of layer " + layer.getName()
                    + " not run, retry once it recovers";
            throw new StorageException(message, failure);
        }
        return truncateStoreThenCache(removeFromCache, truncateStore);
    }

    /**
     * Whether a cache failure fails a truncate: not for a layer not using the cache, whose tiles never go there, nor
     * for an unusable cache, which never held any.
     */
    private boolean truncateNeedsCache() {
        return cached && !cache.getStore().isUnusable();
    }

    /**
     * Truncates the store, then removes from the cache again, so that a tile read through from the store while it was
     * being truncated does not survive.
     *
     * @throws StorageException when the removal fails, the store being truncated already
     */
    private boolean truncateStoreThenCache(Consumer<KeyValueStore> removeFromCache, StoreTruncate truncateStore)
            throws StorageException {
        boolean result = truncateStore.run();
        RuntimeException failure = cacheRemovalFailure(removeFromCache);
        if (failure != null) {
            String message = "In-memory tile cache removal failed after the blob store truncate of layer "
                    + layer.getName() + ", cached tiles may be stale until they expire";
            throw new StorageException(message, failure);
        }
        // with passthrough off the store behind holds no tiles, so its false only means it had nothing to delete
        return result || !storeHoldsTiles();
    }

    /** Runs the removal on the cache; the failure, null on success. */
    @Nullable
    private RuntimeException cacheRemovalFailure(Consumer<KeyValueStore> removeFromCache) {
        try {
            removeFromCache.accept(cache.getStore());
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    /** A truncate of the store behind, true when it removed something. */
    @FunctionalInterface
    private interface StoreTruncate {
        boolean run() throws StorageException;
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        // cache keys use the layer id, which a rename keeps
        return delegate.rename(oldLayerName, newLayerName);
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    @Override
    public void addListener(BlobStoreListener listener) {
        delegate.addListener(listener);
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return delegate.removeListener(listener);
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        return delegate.getLayerMetadata(layerName, key);
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        delegate.putLayerMetadata(layerName, key, value);
    }

    @Override
    public boolean layerExists(String layerName) {
        return delegate.layerExists(layerName);
    }

    @Override
    public Set<String> getParameterIds(String layerName) throws StorageException {
        return delegate.getParameterIds(layerName);
    }

    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        return delegate.getParametersMapping(layerName);
    }
}
