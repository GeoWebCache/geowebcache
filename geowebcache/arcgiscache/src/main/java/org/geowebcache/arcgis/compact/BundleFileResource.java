package org.geowebcache.arcgis.compact;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.geowebcache.io.Resource;

/**
 *
 * @author Bjoern Saxe
 *
 */

public class BundleFileResource implements Resource {
    private final String bundleFilePath;

    private final long tileOffset;

    private final int tileSize;

    public BundleFileResource(String bundleFilePath, long tileOffset, int tileSize) {
        this.bundleFilePath = bundleFilePath;
        this.tileOffset = tileOffset;
        this.tileSize = tileSize;
    }

    /**
     * @see org.geowebcache.io.Resource#getSize()
     */
    public long getSize() {
        return tileSize;
    }

    /**
     * @see org.geowebcache.io.Resource#transferTo()
     */
    public long transferTo(WritableByteChannel target) throws IOException {
        try (FileChannel in = new FileInputStream(new File(bundleFilePath)).getChannel()) {
            final long size = tileSize;
            long written = 0;
            while ((written += in.transferTo(tileOffset + written, size, target)) < size)
                ;
            return size;
        }
    }

    /**
     * Not supported for ArcGIS caches as they are read only.
     *
     * @see org.geowebcache.io.Resource#transferFrom()
     */
    public long transferFrom(ReadableByteChannel channel) throws IOException {
        // unsupported
        return 0;
    }

    /**
     * @see org.geowebcache.io.Resource#getInputStream()
     */
    public InputStream getInputStream() throws IOException {
        FileInputStream fis = new FileInputStream(bundleFilePath);
        fis.skip(tileOffset);

        return fis;
    }

    /**
     * Not supported for ArcGIS caches as they are read only.
     *
     * @see org.geowebcache.io.Resource#getOutputStream()
     */
    public OutputStream getOutputStream() throws IOException {
        // unsupported
        return null;
    }

    /**
     * @see org.geowebcache.io.Resource#getLastModified()
     */
    public long getLastModified() {
        File f = new File(bundleFilePath);

        return f.lastModified();
    }

}