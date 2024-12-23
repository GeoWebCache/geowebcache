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

/** Visitor for a hierarchy of tile files. Roughly inspired by Java own {@link java.nio.file.FileVisitor} */
public interface TileFileVisitor {

    /** Invoked before visitng a directory */
    default void preVisitDirectory(File dir) {}

    /** Invoked on a specific tile file */
    public void visitFile(File tile, long x, long y, int z);

    /** Invoked on a directory post file visit */
    default void postVisitDirectory(File dir) {}
}
