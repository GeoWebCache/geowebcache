package org.geowebcache.arcgis.config;

/**
 * Represents a {@code TileImageInfo} element in an ArcGIS tile cache config file.
 * <p>
 * XML representation:
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
 * </p>
 * 
 * @author Gabriel Roldan
 * 
 */
public class TileImageInfo {

    private String cacheTileFormat;

    private float compressionQuality;

    private boolean antialiasing;

    private int BandCount;

    private float LERCError;

    /**
     * One of {@code PNG8, PNG24, PNG32, JPEG, Mixed}
     * <p>
     * {@code Mixed} uses mostly JPEG, but 32 on the borders of the cache
     * </p>
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
