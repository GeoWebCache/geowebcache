package org.geowebcache.arcgis.config;

/**
 * Represents a {@code LODInfo} (Level Of Detail Info) element in an ArcGIS tile cache config file.
 * <p>
 * XML representation:
 * 
 * <pre>
 * <code>
 *       &lt;LODInfo xsi:type='typens:LODInfo'&gt;
 *         &lt;LevelID&gt;1&lt;/LevelID&gt;
 *         &lt;Scale&gt;6000000&lt;/Scale&gt;
 *         &lt;Resolution&gt;1587.5031750063501&lt;/Resolution&gt;
 *       &lt;/LODInfo&gt;
 * </code>
 * </pre>
 * 
 * </p>
 * 
 * @author Gabriel Roldan
 * 
 */
public class LODInfo {

    private int levelID;

    private double scale;

    private double resolution;

    public int getLevelID() {
        return levelID;
    }

    public double getScale() {
        return scale;
    }

    public double getResolution() {
        return resolution;
    }
}
