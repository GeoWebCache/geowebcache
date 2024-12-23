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
package org.geowebcache.storage.blobstore.file;

import java.io.File;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

/** Encapsulates the generation of file paths and their visit on the file system */
public interface FilePathGenerator {

    /** Generates a file path for the given tile in the desired format */
    File tilePath(TileObject tile, MimeType mimeType) throws GeoWebCacheException;

    /**
     * Visits a directory containing a layer, hitting all tiles matching the tile range, and invoking the visitor on
     * them.
     */
    void visitRange(File layerDirectory, TileRange range, TileFileVisitor visitor) throws StorageException;
}
