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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */
package org.geowebcache.storage.blobstore.file;

import static org.geowebcache.storage.blobstore.file.FilePathUtils.*;

import java.io.File;
import java.io.FilenameFilter;

import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRange;

public class FilePathFilter implements FilenameFilter {

    private final String gridSetPrefix;

    private String mimeExtension;

    private TileRange tr;

    public FilePathFilter(TileRange trObj) throws StorageException {
        this.tr = trObj;

        if (tr.getGridSetId() == null) {
            throw new StorageException("Specifying the grid set id is currently mandatory.");
        }

        gridSetPrefix = filteredGridSetId(tr.getGridSetId());

        if (tr.getMimeType() != null) {
            mimeExtension = tr.getMimeType().getFileExtension();
        }
    }

    /**
     * Assumes it will get fed something like path: name: *EPSG_2163_01/0_0 01_01.png *EPSG_2163_01/
     * 0_0 * EPSG_2163_01
     * 
     * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
     */
    public boolean accept(File parent, String fileName) {
        boolean ret;
        if (fileName.startsWith(gridSetPrefix)) {
            // gridset and zoomlevel level
            ret = acceptZoomLevelDir(fileName);
        } else if (fileName.contains(".")) {
            // filename
            ret = acceptFileName(parent, fileName);
        } else {
            // intermediate
            ret = acceptIntermediateDir(fileName);
        }

        // System.out.println(ret + " " + name);
        return ret;
    }

    /**
     * Example: nyc_01, nyc_05_1,EPSG_2163_01, EPSG_2163_01_7 (i.e.
     * {@code <gridsetPrefix>_<zLevel>[_<parametersId>]})
     */
    private boolean acceptZoomLevelDir(String name) {
        if (!name.startsWith(gridSetPrefix)) {
            return false;
        }

        if (tr.getZoomStart() == -1 && tr.getZoomStop() == -1) {
            // All zoomlevels
            return true;
        } else {
            int tmp = findZoomLevel(gridSetPrefix, name);
            if (tmp < tr.getZoomStart() || tmp > tr.getZoomStop()) {
                return false;
            }
        }

        return true;
    }

    private boolean acceptIntermediateDir(String name) {
        // if(bounds == null) {
        // return true;
        // }

        // For now we'll do all of them,
        // otherwise we have to extract zoomlevel from path
        return true;
    }

    private boolean acceptFileName(File parent, String name) {
        String[] parts = name.split("\\.");

        // Check mime type

        if (!parts[parts.length - 1].equalsIgnoreCase(this.mimeExtension)) {
            return false;
        }

        // Check coordinates
        String[] coords = parts[0].split("_");

        int zoomLevel = findZoomLevel(gridSetPrefix, parent.getParentFile()
                .getName());
        long x = Long.parseLong(coords[0]);
        long y = Long.parseLong(coords[1]);

        boolean contains = tr.contains(x, y, zoomLevel);

        return contains;
    }
}