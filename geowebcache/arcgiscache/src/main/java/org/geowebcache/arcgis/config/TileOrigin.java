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

/**
 * Represents a {@code TileOrigin} element in an ArcGIS cache config file.
 *
 * <p>The upper left point on the tiling grid. The tiling origin is usually not the point where tiles begin to be
 * created; that only happens in the full extent of the map. Usually the tiling origin is far outside the map to ensure
 * that the map area will be covered and that other caches with the same tiling origin can overlay your cache.
 *
 * <p>XML Structure:
 *
 * <pre>
 * <code>
 *     &lt;TileOrigin xsi:type='typens:PointN'&gt;
 *       &lt;X&gt;-4020900&lt;/X&gt;
 *       &lt;Y&gt;19998100&lt;/Y&gt;
 *     &lt;/TileOrigin&gt;
 * </code>
 * </pre>
 *
 * @author Gabriel Roldan
 */
public class TileOrigin {

    private double X;

    private double Y;

    public double getX() {
        return X;
    }

    public double getY() {
        return Y;
    }
}
