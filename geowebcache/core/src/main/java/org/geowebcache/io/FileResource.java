package org.geowebcache.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class FileResource implements Resource {

    private final File file;

    public FileResource(File file) {
        this.file = file;
    }

    /**
     * @see org.geowebcache.io.Resource#getLastModified()
     */
    public long getLastModified() {
        return file.lastModified();
    }

    /**
     * @see org.geowebcache.io.Resource#getSize()
     */
    public long getSize() {
        // avoid a (relatively expensive) call to File.exists(), file.length() returns 0 if the file
        // doesn't exist anyway
        long size = file.length();
        return size == 0 ? -1 : size;
    }

    public long transferTo(WritableByteChannel target) throws IOException {
        FileChannel in = new FileInputStream(file).getChannel();
        // FileLock lock = in.lock();
        try {
            final long size = in.size();
            long written = 0;
            while ((written += in.transferTo(written, size, target)) < size) {
                ;
            }
            return size;
        } finally {
            in.close();
            // lock.release();
        }
    }

    public long transferFrom(ReadableByteChannel channel) throws IOException {
        final FileChannel out = new FileOutputStream(file).getChannel();
        final FileLock lock = out.lock();
        try {
            final int buffsize = 4096;
            long position = 0;
            long read;
            while ((read = out.transferFrom(channel, position, buffsize)) > 0) {
                position += read;
            }
            return position;
        } finally {
            out.close();
            lock.release();
        }
    }

    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    public OutputStream getOutputStream() throws IOException {
        return new FileOutputStream(file);
    }

    public File getFile() {
        return file;
    }
}
