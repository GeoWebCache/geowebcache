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
package org.geowebcache.arcgis.compact;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.geowebcache.io.Resource;

/**
 * Implementation of ArcGIS compact caches for ArcGIS 10.3
 *
 * <p>The compact cache consists of bundle files (*.bundle), that contain an index and the actual image data. Every
 * .bundle file starts with a 64 byte header. After the header 128x128 matrix (16384 tiles) of 8 byte words. The first 5
 * bytes of every word is the offset that points to the tile image data inside the same .bundle file. The next 3 bytes
 * is the size of the image data. The size of the image data is repeated at offset-4 in 4 byte word. Unused index
 * entries use 04|00|00|00|00|00|00|00. If the size is zero than there is no image data available and the index entry
 * is. If the map cache has more than 128 rows or columns it is divided into several .bundle files.
 *
 * @author Bjoern Saxe
 */
public class ArcGISCompactCacheV2 extends ArcGISCompactCache {
    private static final int COMPACT_CACHE_HEADER_LENGTH = 64;

    private BundlxCache indexCache;

    /**
     * Constructs new ArcGIS 10.3 compact cache.
     *
     * @param pathToCacheRoot Path to compact cache directory (usually ".../_alllayers/"). Path must contain directories
     *     for zoom levels (named "Lxx").
     */
    public ArcGISCompactCacheV2(String pathToCacheRoot) {
        if (pathToCacheRoot.endsWith("" + File.separatorChar)) this.pathToCacheRoot = pathToCacheRoot;
        else this.pathToCacheRoot = pathToCacheRoot + File.separatorChar;

        indexCache = new BundlxCache(10000);
    }

    @Override
    public Resource getBundleFileResource(int zoom, int row, int col) {
        if (zoom < 0 || col < 0 || row < 0) return null;

        BundlxCache.CacheKey key = new BundlxCache.CacheKey(zoom, row, col);
        BundlxCache.CacheEntry entry = null;

        Resource res = null;

        if ((entry = indexCache.get(key)) != null) {
            if (entry.size > 0) res = new BundleFileResource(entry.pathToBundleFile, entry.offset, entry.size);
        } else {

            String basePath = buildBundleFilePath(zoom, row, col);
            String pathToBundleFile = basePath + BUNDLE_EXT;

            if (!(new File(pathToBundleFile)).exists()) return null;

            entry = createCacheEntry(pathToBundleFile, row, col);

            if (entry.size > 0) res = new BundleFileResource(pathToBundleFile, entry.offset, entry.size);

            indexCache.put(key, entry);
        }

        return res;
    }

    private BundlxCache.CacheEntry createCacheEntry(String bundleFile, int row, int col) {
        // col and row are inverted for 10.3 caches
        int index = BUNDLX_MAXIDX * (row % BUNDLX_MAXIDX) + (col % BUNDLX_MAXIDX);

        // to save one addtional read, we read all 8 bytes in one read
        ByteBuffer offsetAndSize = readFromLittleEndianFile(bundleFile, (index * 8) + COMPACT_CACHE_HEADER_LENGTH, 8);

        byte[] offsetBytes = new byte[8];
        byte[] sizeBytes = new byte[4];

        offsetAndSize.get(offsetBytes, 0, 5);
        offsetAndSize.get(sizeBytes, 0, 3);

        long tileOffset =
                ByteBuffer.wrap(offsetBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
        int tileSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

        return new BundlxCache.CacheEntry(bundleFile, tileOffset, tileSize);
    }
}
