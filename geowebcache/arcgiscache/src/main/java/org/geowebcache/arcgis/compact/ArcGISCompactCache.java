package org.geowebcache.arcgis.compact;

import org.geowebcache.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents complete ArcGIS compact cache data.
 * <p/>
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

public abstract class ArcGISCompactCache {
    protected static final String BUNDLX_EXT = ".bundlx";

    protected static final String BUNDLE_EXT = ".bundle";

    protected static final int BUNDLX_MAXIDX = 128;

    protected String pathToCacheRoot = "";

    /**
     * Get Resource object for tile.
     *
     * @param zoom Zoom level.
     * @param row  Row of tile.
     * @param col  Column of tile.
     * @return Resource object associated with tile image data if tile exists; null otherwise.
     */
    public abstract Resource getBundleFileResource(int zoom, int row, int col);

    protected String buildBundleFilePath(int zoom, int row, int col) {
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
            .append("C").append(colStr);

        return bundlePath.toString();
    }

    protected ByteBuffer readFromLittleEndianFile(String filePath, long offset, int length) {
        ByteBuffer result = null;

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            file.seek(offset);
            // pad to multiples of 4 so we can use getInt() and getLong()
            int padding = 4 - (length % 4);
            byte data[] = new byte[length + padding];

            if (file.read(data, 0, length) != length)
                throw new IOException("not enough bytes read or reached end of file");

            result = ByteBuffer.wrap(data);
            result.order(ByteOrder.LITTLE_ENDIAN);
        } catch (IOException e) {
            System.err.println(e);
        }

        return result;
    }
}
