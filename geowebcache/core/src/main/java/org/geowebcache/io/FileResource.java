/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
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

    /** @see org.geowebcache.io.Resource#getLastModified() */
    @Override
    public long getLastModified() {
        return file.lastModified();
    }

    /** @see org.geowebcache.io.Resource#getSize() */
    @Override
    public long getSize() {
        // avoid a (relatively expensive) call to File.exists(), file.length() returns 0 if the file
        // doesn't exist anyway
        long size = file.length();
        return size == 0 ? -1 : size;
    }

    @Override
    @SuppressWarnings("PMD.EmptyControlStatement")
    public long transferTo(WritableByteChannel target) throws IOException {
        // FileLock lock = in.lock();

        try (FileInputStream fis = new FileInputStream(file);
                FileChannel in = fis.getChannel()) {
            final long size = in.size();
            long written = 0;
            while ((written += in.transferTo(written, size, target)) < size)
                ;
            return size;
        }
    }

    @Override
    @SuppressWarnings("PMD.UnusedLocalVariable")
    public long transferFrom(ReadableByteChannel channel) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
                FileChannel out = fos.getChannel();
                FileLock lock = out.lock()) {
            final int buffsize = 4096;
            long position = 0;
            long read;
            while ((read = out.transferFrom(channel, position, buffsize)) > 0) {
                position += read;
            }
            return position;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new FileOutputStream(file);
    }

    public File getFile() {
        return file;
    }
}
