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
 * Represents a {@code LODInfo} (Level Of Detail Info) element in an ArcGIS tile cache config file.
 *
 * <p>XML representation:
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
 * @author Gabriel Roldan
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
