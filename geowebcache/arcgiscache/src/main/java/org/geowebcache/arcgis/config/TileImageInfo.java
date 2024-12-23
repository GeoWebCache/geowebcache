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
 * Represents a {@code TileImageInfo} element in an ArcGIS tile cache config file.
 *
 * <p>XML representation:
 *
 * <pre>
 * <code>
 *   &lt;TileImageInfo xsi:type='typens:TileImageInfo'&gt;
 *     &lt;CacheTileFormat&gt;JPEG&lt;/CacheTileFormat&gt;
 *     &lt;CompressionQuality&gt;80&lt;/CompressionQuality&gt;
 *     &lt;Antialiasing&gt;true&lt;/Antialiasing&gt;
 *   &lt;/TileImageInfo&gt;
 * </code>
 * </pre>
 *
 * @author Gabriel Roldan
 */
public class TileImageInfo {

    private String cacheTileFormat;

    private float compressionQuality;

    private boolean antialiasing;

    private int BandCount;

    private float LERCError;

    /**
     * One of {@code PNG8, PNG24, PNG32, JPEG, Mixed}
     *
     * <p>{@code Mixed} uses mostly JPEG, but 32 on the borders of the cache
     */
    public String getCacheTileFormat() {
        return cacheTileFormat;
    }

    public float getCompressionQuality() {
        return compressionQuality;
    }

    public boolean isAntialiasing() {
        return antialiasing;
    }

    public int getBandCount() {
        return BandCount;
    }

    public float getLERCError() {
        return LERCError;
    }
}
