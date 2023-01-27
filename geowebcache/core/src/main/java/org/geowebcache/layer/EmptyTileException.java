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
 * <p>Copyright 2022
 */
package org.geowebcache.layer;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.mime.MimeType;

/**
 * Exception used to indicate no contents was found, but the tile was inside the bounds of the
 * layer. The tile is considered empty, depending on the format that might need a 204, no content,
 * or a 200 with a valid empty tile for the chosen mime type.
 */
public class EmptyTileException extends GeoWebCacheException {

    private final MimeType mime;
    private ByteArrayResource contents;

    /**
     * An empty tile was found, the result contents are provided (e..g., a valid empty tile in the
     * specified format). Should return a 200.
     */
    public EmptyTileException(MimeType mime, ByteArrayResource contents) {
        this(mime);
        this.contents = contents;
    }

    /**
     * An empty tile was found, but no contents are provided. The caller should return a 204, no
     * content.
     */
    public EmptyTileException(MimeType mime) {
        super("No tile data available for this location");
        this.mime = mime;
    }

    public MimeType getMime() {
        return mime;
    }

    public ByteArrayResource getContents() {
        return contents;
    }
}
