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
 * @author Chris Whitney
 *  
 */
package org.geowebcache.mime;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MimeType {

    private static Log log = LogFactory
            .getLog(org.geowebcache.mime.MimeType.class);

    private static final String DEFAULT_MIME = "application/octet-stream";

    private static final char SEPARATOR = '/';

    private String encoding = null;

    private String mimetype = null;

    public MimeType() {
    }

    /**
     * @param mime
     *            is the full MIME string
     * @throws IOException
     */
    public MimeType(String mime) throws IOException {
        setMime(mime);
    }

    public MimeType(String mime, String encoding) throws IOException {
        setMime(mime);
        this.encoding = encoding;
    }

    public void setToDefault() {
        try {
            setMime(DEFAULT_MIME);
        } catch (IOException e) {
            log.error("Invalid default MIME type!");
        }
    }

    /**
     * @return the MIME String
     */
    public String getMime() {
        return (mimetype);
    }

    /**
     * @param mimetype
     *            is the full MIME string
     * @throws IOException
     */
    public void setMime(String mimetype) throws IOException {
        mimetype = mimetype.toLowerCase();
        if (mimetype.length() > 0) {
            this.mimetype = mimetype;
            log.trace("Set MIME type to: " + this.mimetype);
        } else {
            log.error("Cannot set MIME type to null!");
            throw new IOException("Invalid MIME Type: " + mimetype);
        }
    }

    /**
     * @return the type
     */
    public String getFormat() {
        int sep_index = mimetype.indexOf(SEPARATOR);
        sep_index++;
        String format;
        try {
            format = mimetype.substring(sep_index);
        } catch (IndexOutOfBoundsException iobe) {
            format = getMime();
        }
        return format;
    }

    @Override
    public String toString() {
        return getMime();
    }

    public String toFullString() {
        return getMime() + ", " + getEncoding();
    }

    /**
     * @return the encoding
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * @param encoding
     *            the encoding to set
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
        log.trace("Set character encoding to: " + this.encoding);
    }

    public boolean equals(MimeType other) {
        return mimetype.equals(other.getMime());
    }
}
