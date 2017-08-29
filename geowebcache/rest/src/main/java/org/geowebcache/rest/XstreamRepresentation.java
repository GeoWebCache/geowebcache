/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan, The Open Planning Project, 2007
 *
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
