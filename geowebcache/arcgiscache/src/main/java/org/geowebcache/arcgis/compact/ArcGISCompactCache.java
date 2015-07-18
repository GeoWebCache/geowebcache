package org.geowebcache.arcgis.compact;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.geowebcache.io.Resource;

/**
 * Represents complete ArcGIS compact cache data.
 *
 * ArcGIS compact caches consist of bundle index files (*.bundlx) and bundle files (*.bundle), that contain
 * the actual image data.
 * Every .bundlx file contains a 16 byte header and 16 byte footer. Between header and footer is
 * 128x128 matrix (16384 tiles) of 5 byte offsets. Every offset points to a 4 byte word in the
 * corresponding .bundle file which contains the size of the tile image data. The actual image data
 * starts at offset+4. If the size is zero there is no image data available and the index entry is
 * not used. If the map cache has more than 128 rows or columns it is divided into several .bundlx
 * and .bundle files.
 *
 * @author Bjoern Saxe
 */

public class ArcGISCompactCache {
    private static final String BUNDLX_EXT = ".bundlx";

    private static final String BUNDLE_EXT = ".bundle";

    private static final int BUNDLX_MAXIDX = 128;

    private final String pathToCacheRoot;

    private BundlxCache indexCache;

    /**
     * Constructs new ArcGIS compact cache.
     *
     * @param pathToCacheRoot Path to compact cache directory (usually ".../_alllayers/"). Path must contain
     *                        directories for zoom levels (named "Lxx").
     */
    public ArcGISCompactCache(String pathToCacheRoot) {
        if (pathToCacheRoot.endsWith("" + File.separatorChar))
            this.pathToCacheRoot = pathToCacheRoot;
        else
            this.pathToCacheRoot = pathToCacheRoot + File.separatorChar;

        indexCache = new BundlxCache(10000);
    }

    /**
     * Check if tile exists.
     *
     * @param zoom Zoom level of tile.
     * @param row  Row of tile.
     * @param col  Column of tile.
     * @return True if tile exists (actual image data is available); false otherwise.
     */
    public boolean tileExists(int zoom, int row, int col) {
        if (zoom < 0 || col < 0 || row < 0)
            return false;

        boolean exists = false;

        BundlxCache.CacheKey key = new BundlxCache.CacheKey(zoom, row, col);
        BundlxCache.CacheEntry entry = null;

        if ((entry = indexCache.get(key)) != null) {
            exists = entry.size > 0;
        } else {
            String pathToBundleFile = buildBundleFilePath(zoom, row, col, BUNDLE_EXT);
            String pathToBundlxFile = buildBundleFilePath(zoom, row, col, BUNDLX_EXT);

            if (!(new File(pathToBundleFile)).exists() || !(new File(pathToBundlxFile)).exists())
                return false;

            long tileOffset = readTileStartOffset(pathToBundlxFile, row, col);
            int tileSize = readTileSize(pathToBundleFile, tileOffset);

            exists = tileSize > 0;

            entry = new BundlxCache.CacheEntry(pathToBundleFile, tileOffset, tileSize);
            indexCache.put(key, entry);
        }

        return exists;
    }

    /**
     * Get Resource object for tile.
     *
     * @param zoom Zoom level.
     * @param row  Row of tile.
     * @param col  Column of tile.
     * @return Resource object associated with tile image data if tile exists; null otherwise.
     */
    public Resource getBundleFileResource(int zoom, int row, int col) {
        if (zoom < 0 || col < 0 || row < 0)
            return null;

        BundlxCache.CacheKey key = new BundlxCache.CacheKey(zoom, row, col);
        BundlxCache.CacheEntry entry = null;

        Resource res = null;

        if ((entry = indexCache.get(key)) != null) {
            if (entry.size > 0)
                res = new BundleFileResource(entry.pathToBundleFile, entry.offset, entry.size);
        } else {
            String pathToBundleFile = buildBundleFilePath(zoom, row, col, BUNDLE_EXT);
            String pathToBundlxFile = buildBundleFilePath(zoom, row, col, BUNDLX_EXT);

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

    private String buildBundleFilePath(int zoom, int row, int col, String fileExtension) {
        StringBuilder bundlePath = new StringBuilder(pathToCacheRoot);

        int baseRow = (row / BUNDLX_MAXIDX) * BUNDLX_MAXIDX;
        int baseCol = (col / BUNDLX_MAXIDX) * BUNDLX_MAXIDX;

        String zoomStr = Integer.toString(zoom);
        if (zoomStr.length() < 2)
            zoomStr = "0" + zoomStr;

        StringBuilder rowStr = new StringBuilder(Integer.toHexString(baseRow));
        StringBuilder colStr = new StringBuilder(Integer.toHexString(baseCol));

        // column and rows are at least 4 characters long
        final int padding = 4;

        while (colStr.length() < padding)
            colStr.insert(0, "0");

        while (rowStr.length() < padding)
            rowStr.insert(0, "0");

        bundlePath.append("L").append(zoomStr).append(File.separatorChar).append("R").append(rowStr)
            .append("C").append(colStr).append(fileExtension);

        return bundlePath.toString();
    }

    private long readTileStartOffset(String bundlxFile, int row, int col) {
        int index = BUNDLX_MAXIDX * (col % BUNDLX_MAXIDX) + (row % BUNDLX_MAXIDX);

        byte[] data = readFromFile(bundlxFile, (index * 5) + 16, 5);

        ByteBuffer idxBytes = ByteBuffer.allocate(8);
        idxBytes.order(ByteOrder.LITTLE_ENDIAN);
        idxBytes.put(data);
        idxBytes.rewind();

        return idxBytes.getLong();
    }

    private int readTileSize(String bundlxFile, long offset) {
        byte[] data = readFromFile(bundlxFile, offset, 4);
        ByteBuffer tileSize = ByteBuffer.allocate(4);
        tileSize.put(data);
        tileSize.rewind();
        tileSize.order(ByteOrder.LITTLE_ENDIAN);

        return tileSize.getInt();
    }

    private byte[] readFromFile(String filePath, long offset, int length) {
        byte[] result = new byte[0];

        try (FileInputStream fileInputStream = new FileInputStream(
            new File(filePath)); FileChannel fileChannel = fileInputStream.getChannel()) {
            ByteBuffer content = ByteBuffer.allocate(length);
            fileChannel.read(content, offset);

            result = content.array();
        } catch (IOException e) {
            System.err.println(e);
        }

        return result;
    }
}
