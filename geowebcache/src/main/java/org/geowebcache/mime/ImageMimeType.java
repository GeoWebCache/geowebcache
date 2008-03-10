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

public class ImageMimeType extends MimeType {

    private static Log log = LogFactory
            .getLog(org.geowebcache.mime.ImageMimeType.class);

    private static final String IMAGE_MIME_PREFIX = "image/";

    public ImageMimeType() {
        super();
    }

    /**
     * @param mime
     *            is the full MIME string
     * @throws IOException
     */
    public ImageMimeType(String mime) throws IOException {
        super(mime);
    }

    /**
     * @param mime
     *            is
     * @throws IOException
     */
    public ImageMimeType(MimeType mime) throws IOException {
        setMime(mime.getMime());
        setEncoding(mime.getEncoding());
    }

    /**
     * @param mimetype
     *            is the full MIME string
     * @throws IOException
     */
    @Override
    public void setMime(String mimetype) throws IOException {
        if (mimetype.startsWith(IMAGE_MIME_PREFIX)) {
            super.setMime(mimetype);
        } else {
            log.error("Invalid MIME Type: " + mimetype);
            throw new IOException("Invalid MIME Type: " + mimetype);
        }
    }

    /**
     * @param format
     *            is an image format
     */
    public void setFormat(String imageformat) {
        try {
            super.setMime(IMAGE_MIME_PREFIX + imageformat);
        } catch (IOException ioe) {
            log.error("Failed to set Image Format!");
        }
    }

    public static boolean isImageMime(String mime) {
        return (mime.startsWith(IMAGE_MIME_PREFIX));
    }

}
