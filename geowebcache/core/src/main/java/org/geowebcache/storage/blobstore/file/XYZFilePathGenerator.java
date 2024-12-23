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

import static org.geowebcache.storage.blobstore.file.FilePathUtils.appendFiltered;
import static org.geowebcache.util.FileUtils.listFilesNullSafe;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.commons.io.FilenameUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

/**
 * Generates file paths like <code>layerName/gridsetId/z/y/x.extension</code> or <code>
 * layerName/gridsetId_parametersId/z/y/x.extension</code>
 */
public class XYZFilePathGenerator implements FilePathGenerator {

    /** The file layout mode, TMS vs Slippy. */
    public enum Convention {
        /** TMS convention, where Y tile coordinates are numbered * from the south northwards */
        TMS,
        /** Slippy map convention, where tile coordinates have their * origin at top left (NW) */
        XYZ
    }

    @SuppressWarnings("unused")
    private static Logger log = Logging.getLogger(XYZFilePathGenerator.class.getName());

    protected final TileLayerDispatcher layers;
    protected final String cacheRoot;
    private final Convention convention;

    public XYZFilePathGenerator(String cacheRoot, TileLayerDispatcher layers, Convention convention) {
        this.cacheRoot = cacheRoot;
        this.layers = layers;
        this.convention = convention;
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
    public File tilePath(TileObject tile, MimeType mimeType) throws GeoWebCacheException {
        final long[] tileIndex = tile.getXYZ();
        long x = tileIndex[0];
        int z = (int) tileIndex[2];
        long y = getY(tile.getLayerName(), tile.getGridSetId(), x, tileIndex[1], z);

        StringBuilder path = new StringBuilder(256);
        String fileExtension = mimeType.getFileExtension();

        path.append(cacheRoot);
        path.append(File.separatorChar);
        appendFiltered(tile.getLayerName(), path);
        path.append(File.separatorChar);
        String parametersId = tile.getParametersId();
        Map<String, String> parameters = tile.getParameters();
        if (parametersId == null && parameters != null && !parameters.isEmpty()) {
            parametersId = ParametersUtils.getId(parameters);
            tile.setParametersId(parametersId);
        }
        appendFiltered(tile.getGridSetId(), path);
        if (parametersId != null) {
            path.append("_");
            path.append(parametersId);
        }
        path.append(File.separatorChar);
        path.append(z);
        path.append(File.separatorChar);
        path.append(x);
        path.append(File.separatorChar);
        path.append(y);
        path.append('.');
        path.append(fileExtension);

        File tileFile = new File(path.toString());
        return tileFile;
    }

    /**
     * This method abstract going from internal tile grid (TMS) to storage tile grid (could be slippy). One method is
     * all it needs only because the TMS vs Slippy conventions have symmetrical map, e.g., the same extact operation
     * goes both directions
     */
    protected long getY(String layerName, String gridSetId, long x, long y, int z) throws GeoWebCacheException {
        if (convention == Convention.TMS) {
            return y;
        } else {
            TileLayer tileLayer = layers.getTileLayer(layerName);
            GridSubset subset = tileLayer.getGridSubset(gridSetId);
            GridSet gridSet = subset.getGridSet();
            long tilesHigh = gridSet.getGrid(z).getNumTilesHigh();
            return tilesHigh - y - 1;
        }
    }

    @Override
    public void visitRange(File layerDirectory, TileRange range, TileFileVisitor visitor) throws StorageException {
        final FilenameFilter tileFinder = new XYZFilePathFilter(range, this);
        // list directories with gridset and param identifiers
        for (File gridsetParamDir : listFilesNullSafe(layerDirectory, tileFinder)) {
            visitor.preVisitDirectory(gridsetParamDir);

            // go into the zoom level dirs
            for (File zoomDir : listFilesNullSafe(gridsetParamDir, tileFinder)) {
                int z = Integer.parseInt(zoomDir.getName());
                visitor.preVisitDirectory(zoomDir);

                // go into the row column
                for (File xDir : listFilesNullSafe(zoomDir, tileFinder)) {
                    long x = Long.parseLong(xDir.getName());
                    visitor.preVisitDirectory(xDir);

                    // list tiles
                    for (File tile : listFilesNullSafe(xDir, tileFinder)) {
                        long y = Long.parseLong(FilenameUtils.getBaseName(tile.getName()));
                        visitor.visitFile(tile, x, y, z);
                    }

                    visitor.postVisitDirectory(xDir);
                }

                visitor.postVisitDirectory(zoomDir);
            }

            visitor.postVisitDirectory(gridsetParamDir);
        }
    }
}
