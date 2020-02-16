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
package org.geowebcache.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import org.springframework.util.Assert;

public class ByteArrayResource implements Resource, Serializable {

    private byte[] data;

    private int offset;

    private int length;

    private long lastModified = System.currentTimeMillis();

    /** Create a new empty ByteArrayResource */
    public ByteArrayResource() {
        this(null);
    }

    /**
     * Create a new ByteArrayResource from the given byte array.
     *
     * @param data The array of bytes. It will be retained by the Resource for storage.
     */
    public ByteArrayResource(byte[] data) {
        this.data = data;
        this.offset = 0;
        this.length = data == null ? 0 : data.length;
    }

    /**
     * Create a new ByteArrayResource from the given byte array.
     *
     * @param data the array of bytes. It will be retained by the Resource for storage.
     * @param offset the beginning of the portion of the array to use as content
     * @param length the length of the portion of the array to use as content
     */
    public ByteArrayResource(byte[] data, int offset, int length) {
        this.data = data;
        if (data == null) {
            this.data = null;
            this.offset = 0;
            this.length = 0;
        } else {
            this.data = data;
            Assert.isTrue(offset < data.length, "Offset should be less than data length");
            Assert.isTrue(
                    offset + length <= data.length, "Offset + length should be less than length");
            this.offset = offset;
            this.length = length;
        }
    }

    /** Create a new empty ByteArrayResource with a particular initial capacity. */
    public ByteArrayResource(final int initialCapacity) {
        this(new byte[initialCapacity], 0, 0);
    }

    /** @see org.geowebcache.io.Resource#getLastModified() */
    public long getLastModified() {
        return lastModified;
    }

    /** @see org.geowebcache.io.Resource#getSize() */
    public long getSize() {
        return length;
    }

    /** @see org.geowebcache.io.Resource#transferTo(java.nio.channels.WritableByteChannel) */
    public long transferTo(WritableByteChannel channel) throws IOException {
        if (length > 0) {
            ByteBuffer buffer = ByteBuffer.wrap(data, offset, length);
            long written = 0;
            while ((written += channel.write(buffer)) < length) {;
            }
        }
        return length;
    }

    /** @see org.geowebcache.io.Resource#transferFrom(java.nio.channels.ReadableByteChannel) */
    public long transferFrom(ReadableByteChannel channel) throws IOException {
        if (channel instanceof FileChannel) {
            FileChannel fc = (FileChannel) channel;
            offset = 0;
            length = (int) fc.size();
            if (data == null || data.length < length) {
                data = new byte[length];
            }
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int read = 0;
            while ((read += channel.read(buffer)) < length) {;
            }
        } else {
            offset = 0;
            length = 0;
            if (data == null) {
                data = new byte[4096];
            }
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int numRead = 0;
            while ((numRead = channel.read(buffer)) > -1) {
                length += numRead;
                if (buffer.position() == buffer.capacity()) {
                    int position = buffer.position();
                    expand();
                    buffer = ByteBuffer.wrap(data);
                    buffer.position(position);
                }
            }
        }
        return length;
    }

    /** @see org.geowebcache.io.Resource#getInputStream() */
    public SeekableInputStream getInputStream() throws IOException {
        if (data == null) {
            throw new IOException("no data");
        }
        return new SeekableInputStream(this);
    }

    /** Get the contents of the resource. */
    public byte[] getContents() {
        if (data == null || length == 0) {
            return null;
        }
        if (offset == 0 && data.length == length) {
            return data;
        }
        byte[] buff = new byte[length];
        System.arraycopy(data, offset, buff, 0, length);
        return buff;
    }

    /** Discard the contents. */
    public void truncate() {
        offset = 0;
        length = 0;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    /** @see org.geowebcache.io.Resource#getOutputStream() */
    public OutputStream getOutputStream() throws IOException {
        return new SeekableOutputStream(this);
    }

    private void expand() {
        if (data == null) {
            data = new byte[4096];
            length = 0;
            offset = 0;
        } else {
            byte[] newdata = new byte[(int) (data.length * 1.5)];
            System.arraycopy(data, 0, newdata, 0, data.length);
            data = newdata;
        }
    }

    public static final class SeekableInputStream extends ByteArrayInputStream {

        private final long lower;

        public SeekableInputStream(ByteArrayResource res) {
            super(res.data == null ? new byte[0] : res.data, res.offset, res.length);
            lower = res.offset;
        }

        public void seek(long pos) throws IOException {
            if (pos < 0 || pos > super.count) {
                throw new IOException(
                        "Can't seek to pos " + pos + ". buffer is 0.." + (super.count - 1));
            }
            super.pos = (int) (lower + pos);
        }

        public int length() {
            return super.count;
        }
    }

    public static final class SeekableOutputStream extends OutputStream {

        private ByteArrayResource res;

        private int remaining;

        public SeekableOutputStream(ByteArrayResource res) {
            this.res = res;
            remaining = res.data == null ? 0 : res.data.length;
        }

        @Override
        public void write(int b) throws IOException {
            if (remaining == 0) {
                res.expand();
                remaining = res.data.length - res.length;
            }
            res.data[res.length++] = (byte) b;
            --remaining;
        }

        @Override
        public void write(byte buff[], int off, int len) throws IOException {
            if (remaining < len) {
                while (remaining < len) {
                    this.res.expand();
                    remaining = res.data.length - res.length;
                }
            }
            System.arraycopy(buff, off, res.data, res.length, len);
            res.length += len;
            remaining -= len;
        }
    }
}
