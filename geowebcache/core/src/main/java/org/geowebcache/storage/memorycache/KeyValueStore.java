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

import java.util.function.Predicate;

/**
 * Stores byte arrays under string keys, a key prefix naming a set of entries.
 *
 * <p>Entries expire after the time to live given by the caller, and may be dropped earlier, so never keep state here
 * that must survive. Keys and values are opaque to implementations, which must be thread safe and throw an unchecked
 * exception when their backend cannot be reached.
 */
public interface KeyValueStore extends AutoCloseable {

    /** Returns the value, or null when the key is absent or expired. */
    byte[] get(String key);

    /** Stores the value for at most {@code ttlSeconds}, positive. */
    void put(String key, byte[] value, long ttlSeconds);

    /** Stores the value as {@link #put} does, unless the key already holds one. Atomic. */
    void putIfAbsent(String key, byte[] value, long ttlSeconds);

    /** Removes the entry, if any. */
    void remove(String key);

    /** Removes every entry whose key starts with the prefix. Other nodes see the removal on return. */
    void removeByPrefix(String prefix);

    /**
     * Removes the entries under the prefix that the predicate accepts.
     *
     * <p>An implementation may remove more than that and log it. Callers already have to accept losing entries at any
     * time, since storage is ephemeral.
     */
    void removeMatching(String prefix, Predicate<String> remove);

    /** Removes every entry, including entries other nodes wrote. */
    void clear();

    /**
     * Told whether the cache now considers this store reachable, when that changes. Calls may repeat a value but never
     * overlap, and the last one carries the current state. Must return quickly. Does nothing by default.
     */
    default void reachable(boolean reachable) {}

    /**
     * Releases what this store owns. Entries are left in place, and a connection shared with other callers outlives the
     * call.
     */
    @Override
    void close();
}
