package org.geowebcache.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;


public interface Resource {

    /**
     * The size of the resource in bytes.
     * @return
     */
    public long getSize();

    /**
     * Writes the resource to a channel
     * @param channel the channel to write too
     * @return The number of bytes written
     * @throws IOException
     */
    public long transferTo(WritableByteChannel channel) throws IOException;

    /**
     * Overwrites the resource with bytes read from a channel. 
     * @param channel the channel to read from
     * @return The number of bytes read
     * @throws IOException
     */
    public long transferFrom(ReadableByteChannel channel) throws IOException;
    
    /**
     * An InputStream backed by the resource.
     * @return
     * @throws IOException
     */
    public InputStream getInputStream() throws IOException;

    /**
     * An OutputStream backed by the resource.  Writes are appended to the resource.
     * @return
     * @throws IOException
     */
    public OutputStream getOutputStream() throws IOException;

    /**
     * The time the resource was last modified.
     * 
     * @see java.lang.System#currentTimeMillis
     * 
     * @return
     */
    public long getLastModified();
}
