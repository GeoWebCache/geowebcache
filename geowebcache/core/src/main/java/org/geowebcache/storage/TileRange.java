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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 *  
 */
package org.geowebcache.storage;

import java.util.Map;

import org.geowebcache.mime.MimeType;
import org.geowebcache.util.ServletUtils;

/**
 * A 3 dimensional tile range inside a grid set, specified by a range of zooms for fast filtering
 * and a set of (zoom level,xy bounds) specifications
 */
public class TileRange {
    final public String layerName;

    final public String gridSetId;

    final public int zoomStart;

    final public int zoomStop;

    // {zoom}{minx,miny,maxx,maxy}
    final public long[][] rangeBounds;

    final public MimeType mimeType;

    final public Map<String, String> parameters;

    /**
     * @deprecated use {@link #TileRange(String, String, int, int, long[][], MimeType, Map)}
     */
    public TileRange(String layerName, String gridSetId, int zoomStart, int zoomStop,
            long[][] rangeBounds, MimeType mimeType, String parameters) {
        this(layerName, gridSetId, zoomStart, zoomStop, rangeBounds, mimeType, ServletUtils
                .queryStringToMap(parameters));
    }

    public TileRange(String layerName, String gridSetId, int zoomStart, int zoomStop,
            long[][] rangeBounds, MimeType mimeType, Map<String, String> parameters) {
        this.layerName = layerName;
        this.gridSetId = gridSetId;
        this.rangeBounds = rangeBounds;
        this.zoomStart = zoomStart;
        this.zoomStop = zoomStop;
        this.mimeType = mimeType;
        this.parameters = parameters;
    }

    public boolean contains(long[] idx) {
        return contains(idx[0], idx[1], (int) idx[2]);
    }

    public boolean contains(long x, long y, int z) {
        if (z >= zoomStart && z <= zoomStop) {

            long[] rB = rangeBounds[(int) z];

            if (rB[0] <= x && rB[2] >= x && rB[1] <= y && rB[3] >= y) {
                return true;
            }
        }
        return false;
    }
}
