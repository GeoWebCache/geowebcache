package org.geowebcache.layer.wms;

/*
 *    ImageI/O-Ext - OpenSource Java Image translation Library
 *    http://www.geo-solutions.it/
 *    https://imageio-ext.dev.java.net/
 *    (C) 2007 - 2009, GeoSolutions
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    either version 3 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageInputStreamImpl;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * An implementation of <code>ImageInputStream</code> that gets its input from a regular
 * <code>InputStream</code>. No buffering is performed in this adapter hence it is suitable whenever
 * the underlying is is able to perform marking itself, like it happens for a
 * <code>BufferedInputStream</code>.
 * 
 * <p>
 * In general, it is preferable to use a <code>FileCacheImageInputStream</code> or
 * <code>MemoryCacheImageInputStream</code> when reading from a regular <code>InputStream</code>,
 * but this class can help with improving perfomances in some cases.
 * 
 * 
 * @author Simone Giannecchini, GeoSolutions
 */
class ImageInputStreamAdapter extends ImageInputStreamImpl {

    private InputStream is;

    /**
     * Constructs a n<code>ImageInputStreamAdapter</code> that will read from a given
     * <code>InputStream</code>.
     * 
     * @param is
     *            an <code>InputStream</code> to read from.
     * 
     * @exception IllegalArgumentException
     *                if <code>is</code> is <code>null</code>.
     */
    public ImageInputStreamAdapter(InputStream stream) {
        if (stream == null) {
            throw new IllegalArgumentException("is == null!");
        }
        this.is = stream;
    }

    public int read() throws IOException {
        checkClosed();
        bitOffset = 0;
        final int val = is.read();
        if (val != -1)
            streamPos++;
        return val;

    }

    public int read(byte[] b, int off, int len) throws IOException {
        checkClosed();
        // Will throw NullPointerException
        if (off < 0 || len < 0 || off + len > b.length || off + len < 0) {
            throw new IndexOutOfBoundsException("off < 0 || len < 0 || off + len > b.length!");
        }

        bitOffset = 0;
        if (len == 0) {
            return 0;
        }

        final int val = is.read(b, off, len);
        if (val != -1)
            streamPos++;
        streamPos += len;
        return val;

    }

    /**
     * Returns <code>true</code> since this <code>ImageInputStream</code> does not cache data in
     * order to allow seeking backwards but it relies on the underlying <code>InputStream</code>.
     * 
     * @return <code>true</code>.
     * 
     * @see #isCachedMemory
     * @see #isCachedFile
     */
    public boolean isCached() {
        return false;
    }

    /**
     * Returns <code>false</code> since this <code>ImageInputStream</code> does not maintain a eraf
     * cache.
     * 
     * @return <code>false</code>.
     * 
     * @see #isCached
     * @see #isCachedMemory
     */
    public boolean isCachedFile() {
        return false;
    }

    /**
     * Returns <code>false</code> since this <code>ImageInputStream</code> does not maintain a main
     * memory cache.
     * 
     * @return <code>true</code>.
     * 
     * @see #isCached
     * @see #isCachedFile
     */
    public boolean isCachedMemory() {
        return false;
    }

    /**
     * Closes this <code>ImageInputStreamAdapter</code>. The source <code>InputStream</code> is not
     * closed.
     */
    public void close() throws IOException {
        super.close();
        is.close();
    }

    public void mark() {
        if (!is.markSupported())
            throw new UnsupportedOperationException("Mark is not supported by underlying is");
        is.mark(Integer.MAX_VALUE);
    }

    public void reset() throws IOException {
        is.reset();
    }

    public final static ImageInputStream getStream(InputStream stream) {
        if (stream.markSupported() && !(stream instanceof InflaterInputStream))
            return new ImageInputStreamAdapter(stream);
        if (ImageIO.getUseCache())
            try {
                return new FileCacheImageInputStream(stream, null);
            } catch (IOException e) {

            }
        return new MemoryCacheImageInputStream(stream);
    }
}
