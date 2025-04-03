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
package org.geowebcache.storage;

import java.util.Map;
import java.util.TreeMap;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.mime.MimeType;

/**
 * A 3 dimensional tile range inside a grid set, specified by a range of zooms for fast filtering and a set of (zoom
 * level,xy bounds) specifications
 */
public class TileRange {
    private final String layerName;

    private final String gridSetId;

    private final int zoomStart;

    private final int zoomStop;

    // {zoom}{minx,miny,maxx,maxy}
    private final Map<Integer, long[]> rangeBounds;

    private final MimeType mimeType;

    private final Map<String, String> parameters;

    private String parametersId;

    public TileRange(
            String layerName,
            String gridSetId,
            int zoomStart,
            int zoomStop,
            long[][] rangeBounds,
            MimeType mimeType,
            Map<String, String> parameters) {
        this(
                layerName,
                gridSetId,
                zoomStart,
                zoomStop,
                rangeBounds,
                mimeType,
                parameters,
                ParametersUtils.getId(parameters));
    }

    public TileRange(
            String layerName,
            String gridSetId,
            int zoomStart,
            int zoomStop,
            long[][] rangeBounds,
            MimeType mimeType,
            Map<String, String> parameters,
            String parametersId) {
        this.layerName = layerName;
        this.gridSetId = gridSetId;
        if (rangeBounds == null) {
            this.rangeBounds = null;
        } else {
            this.rangeBounds = new TreeMap<>();
            for (long[] bounds : rangeBounds) {
                if (bounds != null) {
                    // could be null in case calling code is only interested in a subset of zoom
                    // levels
                    this.rangeBounds.put(Integer.valueOf((int) bounds[4]), bounds);
                }
            }
        }
        this.zoomStart = zoomStart;
        this.zoomStop = zoomStop;
        this.mimeType = mimeType;
        this.parameters = parameters;
        this.parametersId = parametersId;
    }

    public boolean contains(long[] idx) {
        return contains(idx[0], idx[1], (int) idx[2]);
    }

    public boolean contains(long x, long y, int z) {
        if (null == rangeBounds) {
            return true;
        }

        if (z >= getZoomStart() && z <= getZoomStop()) {

            long[] rB = rangeBounds(z);

            if (rB[0] <= x && rB[2] >= x && rB[1] <= y && rB[3] >= y) {
                return true;
            }
        }
        return false;
    }

    public void setParametersId(String parametersId) {
        this.parametersId = parametersId;
    }

    /** @return the parameters id, or {@code null} if unset */
    public String getParametersId() {
        return parametersId;
    }

    /** @return the zoomStart */
    public int getZoomStart() {
        return zoomStart;
    }

    /** @return the zoomStop */
    public int getZoomStop() {
        return zoomStop;
    }

    /** @return the layerName */
    public String getLayerName() {
        return layerName;
    }

    /** @return the gridSetId */
    public String getGridSetId() {
        return gridSetId;
    }

    /** @return the mimeType */
    public MimeType getMimeType() {
        return mimeType;
    }

    /** @return the parameters */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * Returns the range bounds for the given level, as a <code>minx,miny,maxx,maxy</code> array
     *
     * @return The bounds
     * @throws IllegalArgumentException if the given z level is not between zoomstart and zoomStop
     */
    public long[] rangeBounds(final int zoomLevel) {
        if (zoomLevel < zoomStart) {
            throw new IllegalArgumentException(zoomLevel + " < zoomStart (" + zoomStart + ")");
        }
        if (zoomLevel > zoomStop) {
            throw new IllegalArgumentException(zoomLevel + " > zoomStop (" + zoomStop + ")");
        }
        long[] zlevelBounds = rangeBounds.get(Integer.valueOf(zoomLevel));
        if (zlevelBounds == null) {
            throw new IllegalStateException("Found no range bounds for z level " + zoomLevel + ": " + rangeBounds);
        }
        return zlevelBounds;
    }
}
