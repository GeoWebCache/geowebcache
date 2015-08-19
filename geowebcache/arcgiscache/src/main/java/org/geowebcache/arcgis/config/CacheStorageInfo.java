package org.geowebcache.arcgis.config;

/**
 * Represents a {@code CacheStorageInfo} element in an ArcGIS tile cache config file.
 * <p>
 * This element exists from ArcGIS 10.0 onwards, and defines whether the cache is in "exploded" or
 * "compact" format. As the "compact" format is not documented by ESRI, we only support the
 * "exploded" format.
 * </p>
 * <p>
 * XML representation:
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
 * </p>
 * 
 * @author Gabriel Roldan
 * 
 */
public class CacheStorageInfo {

    public static final String EXPLODED_FORMAT_CODE = "esriMapCacheStorageModeExploded";
    public static final String COMPACT_FORMAT_CODE = "esriMapCacheStorageModeCompact";
    public static final String COMPACT_FORMAT_CODE_V2 = "esriMapCacheStorageModeCompactV2";

    private String storageFormat;

    private int packetSize;

    private Object readResolve() {
        if (storageFormat == null) {
            storageFormat = EXPLODED_FORMAT_CODE;
        }
        return this;
    }

    /**
     * The storage format defined in the config file, defaults to {@link #EXPLODED_FORMAT_CODE
     * exploded format}
     * 
     * @return
     */
    public String getStorageFormat() {
        return storageFormat;
    }

    public int getPacketSize() {
        return packetSize;
    }

}
