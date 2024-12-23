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
 * <p>Copyright 2019
 */
package org.geowebcache.arcgis.config;

import java.util.List;

/**
 * Represents a {@code TileCacheInfo} element in an ArcGIS cache config file.
 *
 * <p>XML Structure:
 *
 * <pre>
 * <code>
 *   &lt;TileCacheInfo xsi:type='typens:TileCacheInfo'&gt;
 *     &lt;SpatialReference xsi:type='typens:ProjectedCoordinateSystem'&gt;
 *       ....
 *     &lt;/SpatialReference&gt;
 *     &lt;TileOrigin xsi:type='typens:PointN'&gt;
 *       &lt;X&gt;-4020900&lt;/X&gt;
 *       &lt;Y&gt;19998100&lt;/Y&gt;
 *     &lt;/TileOrigin&gt;
 *     &lt;TileCols&gt;512&lt;/TileCols&gt;
 *     &lt;TileRows&gt;512&lt;/TileRows&gt;
 *     &lt;DPI&gt;96&lt;/DPI&gt;
 *     &lt;PreciseDPI&gt;96&lt;/PreciseDPI&gt;
 *     &lt;LODInfos xsi:type='typens:ArrayOfLODInfo'&gt;
 *       &lt;LODInfo xsi:type='typens:LODInfo'&gt;
 *         &lt;LevelID&gt;0&lt;/LevelID&gt;
 *         &lt;Scale&gt;8000000&lt;/Scale&gt;
 *         &lt;Resolution&gt;2116.670900008467&lt;/Resolution&gt;
 *       &lt;/LODInfo&gt;
 *       .....
 *     &lt;/LODInfos&gt;
 *   &lt;/TileCacheInfo&gt;
 * </code>
 * </pre>
 *
 * @author Gabriel Roldan
 */
public class TileCacheInfo {

    private SpatialReference spatialReference;

    private TileOrigin tileOrigin;

    private int tileCols;

    private int tileRows;

    private int DPI;

    private int PreciseDPI;

    private List<LODInfo> lodInfos;

    public SpatialReference getSpatialReference() {
        return spatialReference;
    }

    public TileOrigin getTileOrigin() {
        return tileOrigin;
    }

    public int getTileCols() {
        return tileCols;
    }

    public int getTileRows() {
        return tileRows;
    }

    public int getDPI() {
        return DPI;
    }

    /** New in ArcGIS 10.1+ */
    public int getPreciseDPI() {
        return PreciseDPI;
    }

    public List<LODInfo> getLodInfos() {
        return lodInfos;
    }
}
