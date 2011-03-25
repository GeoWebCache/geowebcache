package org.geowebcache.layer;

import java.io.IOException;

import javax.imageio.stream.ImageInputStreamImpl;

import org.geowebcache.io.ByteArrayResource.SeekableInputStream;

public class ResourceImageInputStream extends ImageInputStreamImpl {

    private SeekableInputStream is;

    public ResourceImageInputStream(SeekableInputStream res) throws IOException {
        this.is = res;
    }

    @Override
    public int read() throws IOException {
        checkClosed();
        bitOffset = 0;
        final int val = is.read();
        if (val != -1) {
            streamPos++;
        }
        return val;
    }

    @Override
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
        if (val != -1) {
            streamPos += len;
        }
        return val;
    }

    @Override
    public boolean isCached() {
        return true;
    }

    @Override
    public boolean isCachedMemory() {
        return true;
    }

    @Override
    public long length() {
        return is.length();
    }

    @Override
    public void seek(long pos) throws IOException {
        is.seek(pos);
        super.seek(pos);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        is = null;
    }
}
