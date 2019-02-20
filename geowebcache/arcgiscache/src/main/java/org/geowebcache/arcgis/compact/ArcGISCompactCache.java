/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.arcgis.compact;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.io.Resource;

/**
 * Abstract base class for ArcGIS compact caches.
 *
 * @author Bjoern Saxe
 */
public abstract class ArcGISCompactCache {
    private static final Log logger = LogFactory.getLog(ArcGISCompactCache.class);

    protected static final String BUNDLX_EXT = ".bundlx";

    protected static final String BUNDLE_EXT = ".bundle";

    protected static final int BUNDLX_MAXIDX = 128;

    protected String pathToCacheRoot = "";

    /**
     * Get Resource object for tile.
     *
     * @param zoom Zoom level.
     * @param row Row of tile.
     * @param col Column of tile.
     * @return Resource object associated with tile image data if tile exists; null otherwise.
     */
    public abstract Resource getBundleFileResource(int zoom, int row, int col);

    /**
     * Build path to a bundle from zoom, col, and row without file extension.
     *
     * @param zoom Zoom levl
     * @param row Row
     * @param col Column
     * @return String containing complete path without file extension in the form of
     *     .../Lzz/RrrrrCcccc with the number of c and r at least 4.
     */
    protected String buildBundleFilePath(int zoom, int row, int col) {
        StringBuilder bundlePath = new StringBuilder(pathToCacheRoot);

        int baseRow = (row / BUNDLX_MAXIDX) * BUNDLX_MAXIDX;
        int baseCol = (col / BUNDLX_MAXIDX) * BUNDLX_MAXIDX;

        String zoomStr = Integer.toString(zoom);
        if (zoomStr.length() < 2) zoomStr = "0" + zoomStr;

        StringBuilder rowStr = new StringBuilder(Integer.toHexString(baseRow));
        StringBuilder colStr = new StringBuilder(Integer.toHexString(baseCol));

        // column and rows are at least 4 characters long
        final int padding = 4;

        while (colStr.length() < padding) colStr.insert(0, "0");

        while (rowStr.length() < padding) rowStr.insert(0, "0");

        bundlePath
                .append("L")
                .append(zoomStr)
                .append(File.separatorChar)
                .append("R")
                .append(rowStr)
                .append("C")
                .append(colStr);

        return bundlePath.toString();
    }

    /**
     * Read from a file that uses little endian byte order.
     *
     * @param filePath Path to file
     * @param offset Read at offset
     * @param length Read length bytes
     * @return ByteBuffer that contains read bytes and has byte order set to little endian. The
     *     length of the byte buffer is multiple of 4, so getInt() and getLong() can be used even
     *     when fewer bytes are read.
     */
    protected ByteBuffer readFromLittleEndianFile(String filePath, long offset, int length) {
        ByteBuffer result = null;

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            file.seek(offset);
            // pad to multiples of 4 so we can use getInt() and getLong()
            int padding = 4 - (length % 4);
            byte data[] = new byte[length + padding];

            if (file.read(data, 0, length) != length)
                throw new IOException("not enough bytes read or reached end of file");

            result = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        } catch (IOException e) {
            logger.warn("Failed to read from little endian file", e);
        }

        return result;
    }
}
