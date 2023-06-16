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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.storage.blobstore.file;

import static org.geowebcache.storage.blobstore.file.FilePathUtils.filteredGridSetId;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.filteredLayerName;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.findParameter;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.findZoomLevel;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.FilenameFilter;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRange;

/** Filter for identifying files that represent tiles within a particular range */
public class DefaultFilePathFilter implements FilenameFilter {

    private final String gridSetPrefix;

    private String mimeExtension;

    private TileRange tr;

    private String layerPrefix;

    /**
     * Create a filter for stored tiles that are within a particular range.
     *
     * @param trObj the range to find
     */
    public DefaultFilePathFilter(TileRange trObj) throws StorageException {
        this.tr = trObj;

        if (tr.getGridSetId() == null) {
            throw new StorageException("Specifying the grid set id is currently mandatory.");
        }
        String layerName = tr.getLayerName();
        Preconditions.checkNotNull(layerName);
        this.layerPrefix = filteredLayerName(layerName);

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
    @Override
    public boolean accept(File parent, String fileName) {
        boolean ret = false;
        if (fileName.startsWith(gridSetPrefix)) {
            // gridset and zoomlevel level
            ret = acceptZoomLevelDir(fileName);
        } else if (fileName.contains(".")) {
            // filename
            ret = acceptFileName(parent, fileName);
        } else if (!parent.getName().equals(layerPrefix)) {
            // not a sibling of the gridset prefix (e.g. another gridset), so an intermediate
            ret = acceptIntermediateDir(parent, fileName);
        }

        // System.out.println(ret + " " + name);
        return ret;
    }

    /**
     * Example: nyc_01, nyc_05_1,EPSG_2163_01, EPSG_2163_01_7 (i.e. {@code
     * <gridsetPrefix>_<zLevel>[_<parametersId>]})
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

        String parameter = findParameter(gridSetPrefix, name);
        if (tr.getParametersId() == null) {
            return parameter == null;
        } else {
            return tr.getParametersId().equals(parameter);
        }
    }

    private boolean acceptIntermediateDir(File parent, String name) {
        int z = findZoomLevel(gridSetPrefix, parent.getName());

        // the folder contains a square subset of tiles across X and Y, see
        // DefaultFilePathGenerator#tilePath for the splitting logic
        String[] parts = name.split("_");
        long halfX = Long.parseLong(parts[0]);
        long halfY = Long.parseLong(parts[1]);
        long shift = z / 2;
        long half = 2 << shift;
        long minX = halfX * half;
        long minY = halfY * half;
        long maxX = minX + half;
        long maxY = minY + half;

        long[] rangeBounds = tr.rangeBounds(z);
        long trMinX = rangeBounds[0];
        long trMaxX = rangeBounds[2];
        long trMinY = rangeBounds[1];
        long trMaxY = rangeBounds[3];

        // range intersection in X anx Y
        return trMinX <= maxX && trMaxX >= minX && trMinY <= maxY && trMaxY >= minY;
    }

    private boolean acceptFileName(File parent, String name) {
        String[] parts = name.split("\\.");

        // Check mime type

        if (!parts[parts.length - 1].equalsIgnoreCase(this.mimeExtension)) {
            return false;
        }

        // Check coordinates
        String[] coords = parts[0].split("_");

        int zoomLevel = findZoomLevel(gridSetPrefix, parent.getParentFile().getName());
        long x = Long.parseLong(coords[0]);
        long y = Long.parseLong(coords[1]);

        boolean contains = tr.contains(x, y, zoomLevel);

        return contains;
    }
}
