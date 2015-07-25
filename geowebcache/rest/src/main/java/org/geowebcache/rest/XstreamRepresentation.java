/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geowebcache.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.geowebcache.io.GeoWebCacheXStream;
import org.restlet.data.MediaType;
import org.restlet.resource.StreamRepresentation;

import com.thoughtworks.xstream.XStream;

/**
 *
 */
public class XstreamRepresentation extends StreamRepresentation {

    private XStream xstream;

    private final Object data;

    public XstreamRepresentation(final Object data) {
        super(MediaType.TEXT_XML);
        this.data = data;
        this.xstream = new GeoWebCacheXStream();
        this.xstream.allowTypesByWildcard(new String[]{"org.geowebcache.**"});
    }

    /**
     * Returns the xstream instance used for encoding and decoding.
     */
    public XStream getXStream() {
        return xstream;
    }

    /**
     * @return {@code null}
     * @see org.restlet.resource.Representation#getStream()
     */
    @Override
    public InputStream getStream() throws IOException {
        return null;
    }

    /**
     * @param outputStream
     * @throws IOException
     * @see org.restlet.resource.Representation#write(java.io.OutputStream)
     */
    @Override
    public void write(OutputStream outputStream) throws IOException {
        Writer writer = new OutputStreamWriter(outputStream, "UTF-8");
        xstream.toXML(data, writer);
    }

}
