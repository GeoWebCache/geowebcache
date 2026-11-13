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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.function.Predicate;

/** {@link KeyValueStore} in the local JVM heap, bounded by the total size of its values. */
public class HeapKeyValueStore implements KeyValueStore {

    /** Guava only expires per cache, so the per-entry deadline is checked on read. */
    private record Entry(byte[] value, long expiresAtNanos) {}

    private final Cache<String, Entry> cache;

    private final Ticker ticker;

    /**
     * Creates a store holding at most {@code maxBytes} of values; an expired entry keeps its memory until read or
     * evicted.
     */
    public HeapKeyValueStore(long maxBytes) {
        this(maxBytes, Ticker.systemTicker());
    }

    HeapKeyValueStore(long maxBytes, Ticker ticker) {
        this.ticker = ticker;
        this.cache = CacheBuilder.newBuilder()
                .maximumWeight(maxBytes)
                .weigher((String key, Entry entry) -> entry.value.length)
                .build();
    }

    @Override
    public byte[] get(String key) {
        Entry entry = cache.getIfPresent(key);
        if (entry == null) return null;
        if (expired(entry)) {
            cache.asMap().remove(key, entry);
            return null;
        }
        return entry.value;
    }

    @Override
    public void put(String key, byte[] value, long ttlSeconds) {
        cache.put(key, new Entry(value, ticker.read() + SECONDS.toNanos(ttlSeconds)));
    }

    @Override
    public void putIfAbsent(String key, byte[] value, long ttlSeconds) {
        Entry entry = new Entry(value, ticker.read() + SECONDS.toNanos(ttlSeconds));
        Entry current = cache.asMap().putIfAbsent(key, entry);
        // an expired entry counts as absent, as get reports it so
        if (current != null && expired(current)) cache.asMap().replace(key, current, entry);
    }

    private boolean expired(Entry entry) {
        // nanoTime-style values can overflow, compare the difference
        return ticker.read() - entry.expiresAtNanos >= 0;
    }

    @Override
    public void remove(String key) {
        cache.invalidate(key);
    }

    @Override
    public void removeByPrefix(String prefix) {
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public void removeMatching(String prefix, Predicate<String> remove) {
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix) && remove.test(key));
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public void close() {
        // nothing to release, the entries go with the instance
    }
}
