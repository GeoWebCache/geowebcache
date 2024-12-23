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

import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.blobstore.file.DefaultFilePathGenerator;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;

/**
 * Non-thread safe Resource cache. Currently in-memory only.
 *
 * @author Ian Schneider <ischneider@opengeo.org>
 * @author Kevin Smith, Boundless
 */
public class TransientCache {

    private final int maxTiles;

    private final int maxStorage;

    private final long expireDelay;

    private long currentStorage;

    private Ticker ticker = Ticker.systemTicker();

    /**
     * A path generator that uses the key set as its key to build keys suitable for usage in the in memory transient
     * cache
     */
    private static FilePathGenerator keyGenerator = new DefaultFilePathGenerator("");

    private Map<String, CachedResource> cache = new LinkedHashMap<>() {

        /** serialVersionUID */
        private static final long serialVersionUID = -4106644240603796847L;

        @Override
        protected boolean removeEldestEntry(Entry<String, CachedResource> eldest) {
            return removeEntries(eldest);
        }
    };

    /**
     * @param maxTiles Maximum number of tiles in cache
     * @param maxStorageKB Maximum size of cached data in KiB
     * @param expireDelay Duration for which the cached resource is valid in ms
     */
    public TransientCache(int maxTiles, int maxStorageKB, long expireDelay) {
        this.maxTiles = maxTiles;
        this.maxStorage = maxStorageKB * 1024;
        this.expireDelay = expireDelay;
    }

    /** Count of cached resources. May include expired resources not yet cleared. */
    public int size() {
        return cache.size();
    }

    /** The currently used storage. May include expired resources not yet cleared. */
    public long storageSize() {
        return currentStorage;
    }

    /**
     * Store a resource
     *
     * @param key key to store the resource under
     * @param r the resource to cache
     */
    public void put(String key, Resource r) {
        byte[] buf = new byte[(int) r.getSize()];
        try {
            r.getInputStream().read(buf);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        CachedResource blob = new CachedResource(new ByteArrayResource(buf));
        currentStorage += r.getSize();
        cache.put(key, blob);
    }

    /**
     * Retrieve a resource
     *
     * @return The resource cached under the given key, or null if no resource is cached.
     */
    public Resource get(String key) {
        CachedResource cached = cache.get(key);
        if (cached != null) {
            cache.remove(key);
            currentStorage -= cached.content.getSize();

            if (cached.time + expireDelay < currentTime()) {
                return null;
            } else {
                return cached.content;
            }
        }
        return null;
    }

    /** A timestamp in milliseconds */
    protected long currentTime() {
        return ticker.read() / 1000;
    }

    // Gets called by overridden LinkedHashMap.removeEldestEntry
    private boolean removeEntries(Entry<String, CachedResource> eldest) {
        // iterator returns items in order added so oldest items are first
        Iterator<CachedResource> items = cache.values().iterator();
        while (items.hasNext() && (currentStorage > maxStorage || cache.size() > maxTiles)) {
            CachedResource r = items.next();
            currentStorage -= r.content.getSize();
            items.remove();
        }
        assert currentStorage <= maxStorage;
        assert currentStorage >= 0;
        assert cache.size() <= maxStorage;

        return false;
    }

    public static String computeTransientKey(TileObject tile) {
        try {
            MimeType mime = MimeType.createFromFormat(tile.getBlobFormat());
            return keyGenerator.tilePath(tile, mime).getAbsolutePath();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private class CachedResource {
        Resource content;
        long time;

        public CachedResource(Resource content, long time) {
            super();
            this.content = content;
            this.time = time;
        }

        public CachedResource(Resource content) {
            this(content, currentTime());
        }
    }

    /** Set a time source for computing expiry. */
    public void setTicker(Ticker ticker) {
        Preconditions.checkNotNull(ticker);
        this.ticker = ticker;
    }
}
