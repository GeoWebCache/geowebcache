package org.geowebcache.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public interface Resource {

    public long getSize();

    public long transferTo(WritableByteChannel channel) throws IOException;

    public long transferFrom(ReadableByteChannel channel) throws IOException;
    
    public InputStream getInputStream() throws IOException;

    public OutputStream getOutputStream() throws IOException;

    /**
     * @return
     */
    public long getLastModified();
}
