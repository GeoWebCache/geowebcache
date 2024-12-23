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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.georss;

import java.io.IOException;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.locationtech.jts.geom.Geometry;

/**
 * Consumes a GeoRSS feed and creates a tile range filter based on the feed's geometries for the given tiled layer.
 *
 * <p>I'm not sure yet where the georss seeding will be launched from. In any case, whether it is a user call or a
 * triggered by a configuration option every X time, it should use this class.
 */
class GeoRSSTileRangeBuilder {

    private static final Logger LOGGER = Logging.getLogger(GeoRSSTileRangeBuilder.class.getName());

    private final TileLayer layer;

    private final String gridSetId;

    private final int maxMaskLevel;

    /** Keeps track of the most current GeoRSS entry "updated" property */
    private String lastEntryUpdate;

    /**
     * @param layer The layer to create the mask of affected tiles for
     * @param gridSetId the gridset identifier of the layer
     * @param maxMaskLevel index of the maximum zoom level for which to create a tile matrix for, meaning greater levels
     *     will be downsampled to save memory
     */
    public GeoRSSTileRangeBuilder(final TileLayer layer, final String gridSetId, final int maxMaskLevel) {
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
        this.lastEntryUpdate = "";

        final GridSubset gridSubset = layer.getGridSubset(gridSetId);
        if (gridSubset == null) {
            throw new IllegalArgumentException("no grid subset " + gridSetId + " at " + layer.getName());
        }
    }

    public GeometryRasterMaskBuilder buildTileRangeMask(final GeoRSSReader reader, String previousEntryUpdate)
            throws IOException {

        final GridSubset gridSubset = layer.getGridSubset(gridSetId);
        final int[] metaTilingFactors = layer.getMetaTilingFactors();
        GeometryRasterMaskBuilder matrix = null;

        Entry entry;
        Geometry geom;

        try {
            while ((entry = reader.nextEntry()) != null) {
                if (entry.getUpdated() != null && entry.getUpdated().equals(previousEntryUpdate)) {
                    LOGGER.warning("Skipping entry with id "
                            + entry.getId()
                            + " since it has the same date as our last feed update.");
                } else {
                    if (matrix == null) {
                        matrix = new GeometryRasterMaskBuilder(gridSubset, metaTilingFactors, maxMaskLevel);
                    }

                    // Record the most recent updated entry. Date comparison
                    // done on the String as the format is like
                    // "2010-03-02T15:51:55Z" with the most significant part
                    // first.
                    if ((entry.getUpdated() != null)
                            && ((lastEntryUpdate == null) || (lastEntryUpdate.compareTo(entry.getUpdated()) < 0))) {
                        lastEntryUpdate = entry.getUpdated();
                    }

                    geom = entry.getWhere();
                    matrix.setMasksForGeometry(geom);
                }
            }
        } finally {
            if (matrix != null) {
                matrix.disposeGraphics();
            }
        }

        return matrix;
    }

    /**
     * Returns the value of the most recent updated property value out of all the georss entries processed at
     * {@link #buildTileRangeMask(GeoRSSReader, String)}
     *
     * @return the latest georss updated value, or {@code null} if none was processed
     */
    public String getLastEntryUpdate() {
        return lastEntryUpdate;
    }
}
