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
 * Represents a {@code CacheStorageInfo} element in an ArcGIS tile cache config file.
 *
 * <p>This element exists from ArcGIS 10.0 onwards, and defines whether the cache is in "exploded" or "compact" format.
 * As the "compact" format is not documented by ESRI, we only support the "exploded" format.
 *
 * <p>XML representation:
 *
 * <pre>
 * <code>
 *   &lt;CacheStorageInfo xsi:type='typens:CacheStorageInfo'&gt;
 *     &lt;StorageFormat&gt;esriMapCacheStorageModeExploded&lt;/StorageFormat&gt;
 *     &lt;PacketSize&gt;0&lt;/PacketSize&gt;
 *   &lt;/CacheStorageInfo&gt;
 * </code>
 * </pre>
 *
 * @author Gabriel Roldan
 */
public class CacheStorageInfo {

    public static final String EXPLODED_FORMAT_CODE = "esriMapCacheStorageModeExploded";
    public static final String COMPACT_FORMAT_CODE = "esriMapCacheStorageModeCompact";
    public static final String COMPACT_FORMAT_CODE_V2 = "esriMapCacheStorageModeCompactV2";

    private String storageFormat;

    private int packetSize;

    @SuppressWarnings("UnusedMethod") // required by serialization
    private Object readResolve() {
        if (storageFormat == null) {
            storageFormat = EXPLODED_FORMAT_CODE;
        }
        return this;
    }

    /** The storage format defined in the config file, defaults to {@link #EXPLODED_FORMAT_CODE exploded format} */
    public String getStorageFormat() {
        return storageFormat;
    }

    public int getPacketSize() {
        return packetSize;
    }
}
