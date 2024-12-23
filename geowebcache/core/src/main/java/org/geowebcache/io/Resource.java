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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public interface Resource {

    /** The size of the resource in bytes. */
    public long getSize();

    /**
     * Writes the resource to a channel
     *
     * @param channel the channel to write too
     * @return The number of bytes written
     */
    public long transferTo(WritableByteChannel channel) throws IOException;

    /**
     * Overwrites the resource with bytes read from a channel.
     *
     * @param channel the channel to read from
     * @return The number of bytes read
     */
    public long transferFrom(ReadableByteChannel channel) throws IOException;

    /** An InputStream backed by the resource. */
    public InputStream getInputStream() throws IOException;

    /** An OutputStream backed by the resource. Writes are appended to the resource. */
    public OutputStream getOutputStream() throws IOException;

    /**
     * The time the resource was last modified.
     *
     * @see java.lang.System#currentTimeMillis
     */
    public long getLastModified();
}
