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
 * @author Arne Kepp, OpenGeo, Copyright 2010
 *  
 */
package org.geowebcache.storage;

import java.util.Map;

import org.geowebcache.mime.MimeType;
import org.geowebcache.util.ServletUtils;

/**
 * This class is a TileRange object with an additional filter
 */
public class DiscontinuousTileRange extends TileRange {

    final private TileRangeMask mask;

    /**
     * @deprecated use
     *             {@link #DiscontinuousTileRange(String, String, int, int, RasterMask, MimeType, Map)}
     */
    public DiscontinuousTileRange(String layerName, String gridSetId, int zoomStart, int zoomStop,
            RasterMask rasterMask, MimeType mimeType, String parameters) {
        this(layerName, gridSetId, zoomStart, zoomStop, rasterMask, mimeType, ServletUtils
                .queryStringToMap(parameters));
    }

    public DiscontinuousTileRange(String layerName, String gridSetId, int zoomStart, int zoomStop,
            TileRangeMask rasterMask, MimeType mimeType, Map<String, String> parameters) {

        super(layerName, gridSetId, zoomStart, zoomStop, rasterMask.getGridCoverages(), mimeType,
                parameters);

        this.mask = rasterMask;
    }

    @Override
    public boolean contains(long x, long y, int z) {
        if (super.contains(x, y, z)) {
            return mask.lookup(x, y, z);
        }
        return false;
    }

    @Override
    public boolean contains(long[] idx) {
        return contains(idx[0], idx[1], (int) idx[2]);
    }
}
