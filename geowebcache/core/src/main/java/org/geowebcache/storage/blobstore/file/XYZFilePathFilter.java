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
 * @author Andrea Aime, GeoSolutions, Copyright 2019
 */
package org.geowebcache.storage.blobstore.file;

import static org.geowebcache.storage.blobstore.file.FilePathUtils.filteredGridSetId;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.filteredLayerName;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.FilenameFilter;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRange;

/** Filter for identifying files that represent tiles within a particular range */
public class XYZFilePathFilter implements FilenameFilter {

    private final String gridSetPrefix;
    private final XYZFilePathGenerator generator;

    private String mimeExtension;

    private TileRange tr;

    private String layerPrefix;

    /**
     * Create a filter for stored tiles that are within a particular range.
     *
     * @param trObj the range to find
     */
    public XYZFilePathFilter(TileRange trObj, XYZFilePathGenerator generator) throws StorageException {
        this.tr = trObj;
        this.generator = generator;

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
     * Assumes it will get fed something like path:
     *
     * <ul>
     *   <li>EPSG_2163/0/1/0.png
     *   <li>EPSG_2163/1/0/0
     *   <li>EPSG_2163/1
     * </ul>
     *
     * @see FilenameFilter#accept(File, String)
     */
    @Override
    public boolean accept(File parent, String fileName) {
        if (fileName.equals(gridSetPrefix)) {
            return acceptGridsetDir(fileName);
        } else if (fileName.contains(".")) {
            // filename
            return acceptFileName(parent, fileName);
        } else if (!parent.getName().equals(layerPrefix)) {
            // not a sibling of the gridset prefix (e.g. another gridset), so an intermediate
            return acceptIntermediateDir(parent, fileName);
        }

        return false;
    }

    /** Example: EPSG_4326, EPSG_4326_<parameterIds> */
    private boolean acceptGridsetDir(String name) {
        if (!name.startsWith(gridSetPrefix)) {
            return false;
        }

        String parameter = findParameter(gridSetPrefix, name);
        if (tr.getParametersId() == null) {
            return parameter == null;
        } else {
            return tr.getParametersId().equals(parameter);
        }
    }

    /**
     * Extracts the parametersId from {@code <gridsetPrefix>_<zLevel>[_<parametersId>]})
     *
     * @precondition {@code dirName.startsWith(gridsetPrefix + "_")}
     */
    String findParameter(final String gridsetPrefix, final String dirName) {
        String prefix = gridsetPrefix + "_";
        if (!dirName.startsWith(prefix) || dirName.length() <= prefix.length()) {
            return null;
        }
        return dirName.substring(prefix.length() + 1);
    }

    /** Can only check the zoom levels */
    private boolean acceptIntermediateDir(File parent, String fileName) {
        try {
            if (acceptGridsetDir(parent.getName())) {
                // the z level directory
                int z = Integer.parseInt(fileName);
                return tr.getZoomStart() <= z && tr.getZoomStop() >= z;
            } else if (acceptGridsetDir(parent.getParentFile().getName())) {
                // a z/x directory
                int z = Integer.parseInt(parent.getName());
                long x = Long.parseLong(fileName);
                long[] xBounds = tr.rangeBounds(z);
                return xBounds[0] <= x && xBounds[2] >= x;
            }
        } catch (IllegalArgumentException e) {
            // ignoring on purpose, one of the dirs must not have followed the naming pattern
        }
        return false;
    }

    private boolean acceptFileName(File parent, String name) {
        String[] parts = name.split("\\.");
        try {
            if (parts.length != 2) {
                return false;
            }

            // Check mime type
            if (!parts[parts.length - 1].equalsIgnoreCase(this.mimeExtension)) {
                return false;
            }

            long y = Long.parseLong(parts[0]);
            long x = Long.parseLong(parent.getName());
            int z = Integer.parseInt(parent.getParentFile().getName());
            // adjust y based on the tms vs slippy convention
            y = generator.getY(tr.getLayerName(), tr.getGridSetId(), x, y, z);

            return tr.contains(x, y, z);
        } catch (Exception e) {
            return false;
        }
    }
}
