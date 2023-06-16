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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.Resource;

/** @author Bjoern Saxe */
public class BundleFileResource implements Resource {
    private static Logger log = Logging.getLogger(BundleFileResource.class.getName());

    private final String bundleFilePath;

    private final long tileOffset;

    private final int tileSize;

    public BundleFileResource(String bundleFilePath, long tileOffset, int tileSize) {
        this.bundleFilePath = bundleFilePath;
        this.tileOffset = tileOffset;
        this.tileSize = tileSize;
    }

    /** @see org.geowebcache.io.Resource#getSize() */
    @Override
    public long getSize() {
        return tileSize;
    }

    /** @see org.geowebcache.io.Resource#transferTo(WritableByteChannel) */
    @Override
    @SuppressWarnings("PMD.EmptyControlStatement")
    public long transferTo(WritableByteChannel target) throws IOException {
        try (FileInputStream fin = new FileInputStream(new File(bundleFilePath));
                FileChannel in = fin.getChannel()) {
            final long size = tileSize;
            long written = 0;
            while ((written += in.transferTo(tileOffset + written, size, target)) < size) ;
            return size;
        }
    }

    /**
     * Not supported for ArcGIS caches as they are read only.
     *
     * @see org.geowebcache.io.Resource#transferFrom(ReadableByteChannel)
     */
    @Override
    public long transferFrom(ReadableByteChannel channel) throws IOException {
        // unsupported
        return 0;
    }

    /** @see org.geowebcache.io.Resource#getInputStream() */
    @Override
    public InputStream getInputStream() throws IOException {
        FileInputStream fis = new FileInputStream(bundleFilePath);
        long skipped = fis.skip(tileOffset);
        if (skipped != tileOffset) {
            log.log(
                    Level.SEVERE,
                    "tried to skip to tile offset "
                            + tileOffset
                            + " in "
                            + bundleFilePath
                            + " but skipped "
                            + skipped
                            + " instead.");
        }
        return fis;
    }

    /**
     * Not supported for ArcGIS caches as they are read only.
     *
     * @see org.geowebcache.io.Resource#getOutputStream()
     */
    @Override
    public OutputStream getOutputStream() throws IOException {
        // unsupported
        return null;
    }

    /** @see org.geowebcache.io.Resource#getLastModified() */
    @Override
    public long getLastModified() {
        File f = new File(bundleFilePath);

        return f.lastModified();
    }
}
