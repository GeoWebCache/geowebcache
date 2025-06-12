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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.filter.request;

import java.io.Serial;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;

/**
 * This is a test filter for the new request filter core.
 *
 * <p>It is not really useful other than to illustrate the concept.
 *
 * <p>Basically it takes the extent of the layer and constructs a a circle that covers half the
 */
public class CircularExtentFilter extends RequestFilter {

    @Serial
    private static final long serialVersionUID = -8488899568092162423L;

    CircularExtentFilter() {}

    @Override
    public void apply(ConveyorTile convTile) throws RequestFilterException {
        TileLayer tl = convTile.getLayer();
        // SRS srs = convTile.getSRS();
        GridSubset gridSubset = tl.getGridSubset(convTile.getGridSetId());

        int z = (int) convTile.getTileIndex()[2];
        long[] gridCoverage = gridSubset.getCoverage(z);

        // Figure out the radius
        long width = gridCoverage[2] - gridCoverage[0];
        long height = gridCoverage[3] - gridCoverage[1];

        // Rounding must always err on the side of
        // caution if you want to use KML hierarchies
        long maxRad = 0;
        if (width > height) {
            maxRad = (width / 4) + 1;
        } else {
            maxRad = (height / 4) + 1;
        }

        // Figure out how the requested bounds relate
        long midX = gridCoverage[0] + width / 2;
        long midY = gridCoverage[1] + height / 2;

        long xDist = midX - convTile.getTileIndex()[0];
        long yDist = midY - convTile.getTileIndex()[1];

        long rad = Math.round(Math.sqrt(xDist * xDist + yDist * yDist));

        if (rad > maxRad) {
            throw new BlankTileException(this);
        }
    }

    @Override
    public void initialize(TileLayer layer) throws GeoWebCacheException {
        // Do nothing
    }

    @Override
    public void update(TileLayer layer, String gridSetId, int zoomStart, int zoomStop) throws GeoWebCacheException {
        // Do nothing
    }

    @Override
    public void update(byte[] filterData, TileLayer layer, String gridSetId, int z) throws GeoWebCacheException {
        // Do nothing
    }

    @Override
    public boolean update(TileLayer layer, String gridSetId) {
        // Do nothing
        return false;
    }
}
