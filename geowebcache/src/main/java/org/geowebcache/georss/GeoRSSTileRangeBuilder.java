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
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.georss;

import java.io.IOException;

import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;

import com.vividsolutions.jts.geom.Geometry;

/**
 * Consumes a GeoRSS feed and creates a tile range filter based on the feed's geometries for the
 * given tiled layer.
 * <p>
 * I'm not sure yet where the georss seeding will be launched from. In any case, whether it is a
 * user call or a triggered by a configuration option every X time, it should use this class.
 * </p>
 */
class GeoRSSTileRangeBuilder {

    private final TileLayer layer;

    private final String gridSetId;

    private final int maxMaskLevel;

    /**
     * 
     * @param layer
     *            The layer to create the mask of affected tiles for
     * @param gridSetId
     *            the gridset identifier of the layer
     * @param maxMaskLevel
     *            index of the maximum zoom level for which to create a tile matrix for, meaning
     *            greater levels will be downsampled to save memory
     */
    public GeoRSSTileRangeBuilder(final TileLayer layer, final String gridSetId,
            final int maxMaskLevel) {
        if (layer == null) {
            throw new NullPointerException("layer");
        }
        if (gridSetId == null) {
            throw new NullPointerException("griSetId");
        }
        if (maxMaskLevel < 0) {
            throw new IllegalArgumentException("maxMaskLevel shall be >= 0: " + maxMaskLevel);
        }
        this.layer = layer;
        this.gridSetId = gridSetId;
        this.maxMaskLevel = maxMaskLevel;

        final GridSubset gridSubset = layer.getGridSubset(gridSetId);
        if (gridSubset == null) {
            throw new IllegalArgumentException("no grid subset " + gridSetId + " at "
                    + layer.getName());
        }
    }

    public TileGridFilterMatrix buildTileRangeMask(final GeoRSSReader reader) throws IOException {

        final GridSubset gridSubset = layer.getGridSubset(gridSetId);
        final int[] metaTilingFactors = layer.getMetaTilingFactors();
        TileGridFilterMatrix matrix = new TileGridFilterMatrix(gridSubset, metaTilingFactors, maxMaskLevel);

        Entry entry;
        Geometry geom;

        matrix.createGraphics();
        try {
            while ((entry = reader.nextEntry()) != null) {
                // crs = entry.getCRS();
                geom = entry.getWhere();
                matrix.setMasksForGeometry(geom);
            }
        } finally {
            matrix.disposeGraphics();
        }

        return matrix;
    }
}
