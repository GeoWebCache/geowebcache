/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.geowebcache.storage;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;

/**
 * Non-thread safe Resource cache. Currently in-memory only.
 * 
 * @author Ian Schneider <ischneider@opengeo.org>
 */
public class TransientCache {

    private final int maxTiles;

    private final int maxStorage;

    private long currentStorage;

    /**
     * A path generator that uses the key set as its key to build keys suitable for usage in the in
     * memory transient cache
     */
    private static FilePathGenerator keyGenerator = new FilePathGenerator("");

    private Map<String, Resource> cache = new LinkedHashMap<String, Resource>() {

        @Override
        protected boolean removeEldestEntry(Entry<String, Resource> eldest) {
            return removeEntries(eldest);
        }

    };

    public TransientCache(int maxTiles, int maxStorageKB) {
        this.maxTiles = maxTiles;
        this.maxStorage = maxStorageKB * 1024;
    }

    public int size() {
        return cache.size();
    }

    public long storageSize() {
        return currentStorage;
    }

    public void put(String key, Resource r) {
        byte[] buf = new byte[(int) r.getSize()];
        try {
            r.getInputStream().read(buf);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        ByteArrayResource blob = new ByteArrayResource(buf);
        currentStorage += r.getSize();
        cache.put(key, blob);
    }

    public Resource get(String key) {
        Resource cached = cache.get(key);
        if (cached != null) {
            cache.remove(key);
            currentStorage -= cached.getSize();
        }
        return cached;
    }

    /**
     * Loop through elements, removing and recompute currentStorage size
     */
    private void clean() {
        // iterator returns items in order added so oldest items are first
        Iterator<Resource> items = cache.values().iterator();
        long storage = currentStorage;
        while (items.hasNext()) {
            Resource r = items.next();
            storage -= r.getSize();
            if (storage < maxStorage) {
                break;
            } else {
                items.remove();
            }
        }
        currentStorage = storage;
    }

    private boolean removeEntries(Entry<String, Resource> eldest) {
        boolean remove = false;
        // not sure if we can do both at the same time?
        if (currentStorage > maxStorage) {
            clean();
        } else if (cache.size() > maxTiles) {
            remove = true;
        }
        return remove;
    }

    public static String computeTransientKey(TileObject tile) {
        try {
            MimeType mime = MimeType.createFromFormat(tile.getBlobFormat());
            return keyGenerator.tilePath(tile, mime).getAbsolutePath();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
