package org.geowebcache.arcgis.compact;

import org.geowebcache.io.Resource;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Created by backe on 18/08/15.
 */
public class ArcGISCompactCacheV1 extends ArcGISCompactCache {
    private static final int COMPACT_CACHE_HEADER_LENGTH = 16;

    private BundlxCache indexCache;

    /**
     * Constructs new ArcGIS compact cache.
     *
     * @param pathToCacheRoot Path to compact cache directory (usually ".../_alllayers/"). Path must contain
     *                        directories for zoom levels (named "Lxx").
     */
    public ArcGISCompactCacheV1(String pathToCacheRoot) {
        if (pathToCacheRoot.endsWith("" + File.separatorChar))
            this.pathToCacheRoot = pathToCacheRoot;
        else
            this.pathToCacheRoot = pathToCacheRoot + File.separatorChar;

        indexCache = new BundlxCache(10000);
    }

    @Override public Resource getBundleFileResource(int zoom, int row, int col) {
        if (zoom < 0 || col < 0 || row < 0)
            return null;

        BundlxCache.CacheKey key = new BundlxCache.CacheKey(zoom, row, col);
        BundlxCache.CacheEntry entry = null;

        Resource res = null;

        if ((entry = indexCache.get(key)) != null) {
            if (entry.size > 0)
                res = new BundleFileResource(entry.pathToBundleFile, entry.offset, entry.size);
        } else {

            String basePath = buildBundleFilePath(zoom, row, col);
            String pathToBundlxFile = basePath + BUNDLX_EXT;
            String pathToBundleFile = basePath + BUNDLE_EXT;

            if (!(new File(pathToBundleFile)).exists() || !(new File(pathToBundlxFile)).exists())
                return null;

            long tileOffset = readTileStartOffset(pathToBundlxFile, row, col);
            int tileSize = readTileSize(pathToBundleFile, tileOffset);

            tileOffset += 4;

            if (tileSize > 0)
                res = new BundleFileResource(pathToBundleFile, tileOffset, tileSize);

            entry = new BundlxCache.CacheEntry(pathToBundleFile, tileOffset, tileSize);

            indexCache.put(key, entry);
        }

        return res;
    }

    private long readTileStartOffset(String bundlxFile, int row, int col) {
        int index = BUNDLX_MAXIDX * (col % BUNDLX_MAXIDX) + (row % BUNDLX_MAXIDX);

        ByteBuffer idxBytes = readFromLittleEndianFile(bundlxFile,
            (index * 5) + COMPACT_CACHE_HEADER_LENGTH, 5);

        return idxBytes.getLong();
    }

    private int readTileSize(String bundlxFile, long offset) {
        ByteBuffer tileSize = readFromLittleEndianFile(bundlxFile, offset, 4);

        return tileSize.getInt();
    }
}
