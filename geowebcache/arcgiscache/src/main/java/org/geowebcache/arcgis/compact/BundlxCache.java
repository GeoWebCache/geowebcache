package org.geowebcache.arcgis.compact;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Cache that stores data from .bundlx files.
 *
 * Zoom, row, and column of the tile are used as key. Entries contain the path to the .bundle file,
 * the size of the tile and the offset of the image data inside the .bundle file.
 *
 * @author Bjoern Saxe
 */

public class BundlxCache {
    public static class CacheKey {
        public final int zoom;

        public final int row;

        public final int col;

        public CacheKey(int zoom, int row, int col) {
            this.zoom = zoom;
            this.row = row;
            this.col = col;
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            CacheKey cacheKey = (CacheKey) o;

            if (zoom != cacheKey.zoom)
                return false;
            if (row != cacheKey.row)
                return false;
            return col == cacheKey.col;

        }

        @Override public int hashCode() {
            int result = zoom;
            result = 31 * result + row;
            result = 31 * result + col;
            return result;
        }
    }

    public static class CacheEntry {
        public CacheEntry(String pathToBundleFile, long offset, int size) {
            this.pathToBundleFile = pathToBundleFile;
            this.offset = offset;
            this.size = size;
        }

        public String pathToBundleFile;

        public long offset;

        public int size;
    }

    private Cache<CacheKey, CacheEntry> indexCache;

    /**
     * Cache that stores the path ot the .
     *
     * @param maxSize Maximum size of cache. If the size of the cache equals maxSize, adding a new
     *                entry will remove the least recently used entry from the cache.
     */
    public BundlxCache(int maxSize) {
        indexCache = CacheBuilder.newBuilder().maximumSize(maxSize).build();
    }

    /**
     * Get the entry for a key from the cache.
     *
     * @param key Key.
     * @return Returns the entry. Returns null if the key has a null value or if the key has no entry.
     */
    public synchronized CacheEntry get(CacheKey key) {
        return indexCache.getIfPresent(key);
    }

    /**
     * Puts a key-entry mapping into this cache.
     *
     * @param key the key to add.
     * @param entry the entry to add.
     */
    public synchronized void put(CacheKey key, CacheEntry entry) {
        indexCache.put(key, entry);
    }

}
