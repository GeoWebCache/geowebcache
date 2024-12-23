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
import org.geowebcache.io.Resource;

/**
 * Implementation of ArcGIS compact caches for ArcGIS 10.0 - 10.2
 *
 * <p>The compact cache consists of bundle index files (*.bundlx) and bundle files (*.bundle), that contain the actual
 * image data. Every .bundlx file contains a 16 byte header and 16 byte footer. Between header and footer is 128x128
 * matrix (16384 tiles) of 5 byte offsets. Every offset points to a 4 byte word in the corresponding .bundle file which
 * contains the size of the tile image data. The actual image data starts at offset+4. If the size is zero there is no
 * image data available and the index entry is not used. If the map cache has more than 128 rows or columns it is
 * divided into several .bundlx and .bundle files.
 *
 * @author Bjoern Saxe
 */
public class ArcGISCompactCacheV1 extends ArcGISCompactCache {
    private static final int COMPACT_CACHE_HEADER_LENGTH = 16;

    private BundlxCache indexCache;

    /**
     * Constructs new ArcGIS 10.0-10.2 compact cache.
     *
     * @param pathToCacheRoot Path to compact cache directory (usually ".../_alllayers/"). Path must contain directories
     *     for zoom levels (named "Lxx").
     */
    public ArcGISCompactCacheV1(String pathToCacheRoot) {
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
            String pathToBundlxFile = basePath + BUNDLX_EXT;
            String pathToBundleFile = basePath + BUNDLE_EXT;

            if (!(new File(pathToBundleFile)).exists() || !(new File(pathToBundlxFile)).exists()) return null;

            long tileOffset = readTileStartOffset(pathToBundlxFile, row, col);
            int tileSize = readTileSize(pathToBundleFile, tileOffset);

            tileOffset += 4;

            if (tileSize > 0) res = new BundleFileResource(pathToBundleFile, tileOffset, tileSize);

            entry = new BundlxCache.CacheEntry(pathToBundleFile, tileOffset, tileSize);

            indexCache.put(key, entry);
        }

        return res;
    }

    private long readTileStartOffset(String bundlxFile, int row, int col) {
        int index = BUNDLX_MAXIDX * (col % BUNDLX_MAXIDX) + (row % BUNDLX_MAXIDX);

        ByteBuffer idxBytes = readFromLittleEndianFile(bundlxFile, (index * 5) + COMPACT_CACHE_HEADER_LENGTH, 5);

        return idxBytes.getLong();
    }

    private int readTileSize(String bundlxFile, long offset) {
        ByteBuffer tileSize = readFromLittleEndianFile(bundlxFile, offset, 4);

        return tileSize.getInt();
    }
}
