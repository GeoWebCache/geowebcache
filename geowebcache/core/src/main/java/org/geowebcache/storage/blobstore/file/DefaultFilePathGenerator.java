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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.storage.blobstore.file;

import static org.geowebcache.storage.blobstore.file.FilePathUtils.appendFiltered;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.appendGridsetZoomLevelDir;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.filteredGridSetId;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.findZoomLevel;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.zeroPadder;
import static org.geowebcache.util.FileUtils.listFilesNullSafe;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

public class DefaultFilePathGenerator implements FilePathGenerator {

    @SuppressWarnings("unused")
    private static Logger log = Logging.getLogger(DefaultFilePathGenerator.class.getName());

    String cacheRoot;

    public DefaultFilePathGenerator(String cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    /**
     * Builds the storage path for a tile and returns it as a File reference
     *
     * <p>
     *
     * @param tile information about the tile
     * @param mimeType the storage mime type
     * @return File pointer to the tile image
     */
    @Override
    public File tilePath(TileObject tile, MimeType mimeType) {
        final long[] tileIndex = tile.getXYZ();
        long x = tileIndex[0];
        long y = tileIndex[1];
        long z = tileIndex[2];

        StringBuilder path = new StringBuilder(256);

        long shift = z / 2;
        long half = 2L << shift;
        int digits = 1;
        if (half > 10) {
            digits = (int) (Math.log10(half)) + 1;
        }
        long halfx = x / half;
        long halfy = y / half;

        String fileExtension = mimeType.getFileExtension();

        path.append(cacheRoot);
        path.append(File.separatorChar);
        appendFiltered(tile.getLayerName(), path);
        path.append(File.separatorChar);
        appendGridsetZoomLevelDir(tile.getGridSetId(), z, path);
        String parametersId = tile.getParametersId();
        Map<String, String> parameters = tile.getParameters();
        if (parametersId == null && parameters != null && !parameters.isEmpty()) {
            parametersId = ParametersUtils.getId(parameters);
            tile.setParametersId(parametersId);
        }
        if (parametersId != null) {
            path.append('_');
            path.append(parametersId);
        }
        path.append(File.separatorChar);
        zeroPadder(halfx, digits, path);
        path.append('_');
        zeroPadder(halfy, digits, path);
        path.append(File.separatorChar);

        zeroPadder(x, 2 * digits, path);
        path.append('_');
        zeroPadder(y, 2 * digits, path);
        path.append('.');
        path.append(fileExtension);

        File tileFile = new File(path.toString());
        return tileFile;
    }

    @Override
    public void visitRange(File layerDirectory, TileRange range, TileFileVisitor visitor) throws StorageException {
        final FilenameFilter tileFinder = new DefaultFilePathFilter(range);
        File[] srsZoomDirs = listFilesNullSafe(layerDirectory, tileFinder);

        final String gridsetPrefix = filteredGridSetId(range.getGridSetId());
        for (File srsZoomParamId : srsZoomDirs) {
            visitor.preVisitDirectory(srsZoomParamId);
            int zoomLevel = findZoomLevel(gridsetPrefix, srsZoomParamId.getName());
            File[] intermediates = listFilesNullSafe(srsZoomParamId, tileFinder);

            for (File imd : intermediates) {
                visitor.preVisitDirectory(imd);
                File[] tiles = listFilesNullSafe(imd, tileFinder);

                for (File tile : tiles) {
                    String[] coords = tile.getName().split("\\.")[0].split("_");
                    long x = Long.parseLong(coords[0]);
                    long y = Long.parseLong(coords[1]);
                    visitor.visitFile(tile, x, y, zoomLevel);
                }

                // Try deleting the directory (will be done only if the directory is empty)
                visitor.postVisitDirectory(imd);
            }

            // Try deleting the zoom directory (will be done only if the directory is empty)
            visitor.postVisitDirectory(srsZoomParamId);
        }
    }
}
