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
 * Represents an ArcGIS tile cache configuration file.
 *
 * <p>XML structure:
 *
 * <pre>
 * <code>
 * &lt;CacheInfo xsi:type='typens:CacheInfo' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:xs='http://www.w3.org/2001/XMLSchema'
 *   xmlns:typens='http://www.esri.com/schemas/ArcGIS/10.0'&gt;
 *   &lt;TileCacheInfo xsi:type='typens:TileCacheInfo'&gt;
 *     &lt;SpatialReference xsi:type='typens:ProjectedCoordinateSystem'&gt;
 *     ....
 *     &lt;/SpatialReference&gt;
 *     &lt;TileOrigin xsi:type='typens:PointN'&gt;
 *       &lt;X&gt;-4020900&lt;/X&gt;
 *       &lt;Y&gt;19998100&lt;/Y&gt;
 *     &lt;/TileOrigin&gt;
 *     &lt;TileCols&gt;512&lt;/TileCols&gt;
 *     &lt;TileRows&gt;512&lt;/TileRows&gt;
 *     &lt;DPI&gt;96&lt;/DPI&gt;
 *     &lt;LODInfos xsi:type='typens:ArrayOfLODInfo'&gt;
 *       &lt;LODInfo xsi:type='typens:LODInfo'&gt;
 *         &lt;LevelID&gt;0&lt;/LevelID&gt;
 *         &lt;Scale&gt;8000000&lt;/Scale&gt;
 *         &lt;Resolution&gt;2116.670900008467&lt;/Resolution&gt;
 *       &lt;/LODInfo&gt;
 *
 *       ....
 *
 *     &lt;/LODInfos&gt;
 *   &lt;/TileCacheInfo&gt;
 *   &lt;TileImageInfo xsi:type='typens:TileImageInfo'&gt;
 *     &lt;CacheTileFormat&gt;JPEG&lt;/CacheTileFormat&gt;
 *     &lt;CompressionQuality&gt;80&lt;/CompressionQuality&gt;
 *     &lt;Antialiasing&gt;true&lt;/Antialiasing&gt;
 *   &lt;/TileImageInfo&gt;
 *   &lt;!-- this element is new in 10.0 --&gt;
 *   &lt;CacheStorageInfo xsi:type='typens:CacheStorageInfo'&gt;
 *     &lt;StorageFormat&gt;esriMapCacheStorageModeExploded&lt;/StorageFormat&gt;
 *     &lt;PacketSize&gt;0&lt;/PacketSize&gt;
 *   &lt;/CacheStorageInfo&gt;
 * &lt;/CacheInfo&gt;
 * </code>
 * </pre>
 *
 * @author Gabriel Roldan
 * @see TileCacheInfo
 * @see SpatialReference
 * @see LODInfo
 * @see TileImageInfo
 * @see CacheStorageInfo
 */
public class CacheInfo {

    private TileCacheInfo tileCacheInfo;

    private TileImageInfo tileImageInfo;

    private CacheStorageInfo cacheStorageInfo;

    @SuppressWarnings("UnusedMethod") // required by serialization
    private Object readResolve() {
        if (cacheStorageInfo == null) {
            cacheStorageInfo = new CacheStorageInfo();
        }
        return this;
    }

    public TileCacheInfo getTileCacheInfo() {
        return tileCacheInfo;
    }

    public TileImageInfo getTileImageInfo() {
        return tileImageInfo;
    }

    public CacheStorageInfo getCacheStorageInfo() {
        return cacheStorageInfo;
    }
}
