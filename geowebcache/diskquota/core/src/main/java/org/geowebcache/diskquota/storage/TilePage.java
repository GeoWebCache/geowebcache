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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota.storage;

/**
 * A rectangular group of tiles in a specific TileSet zoom level. The size of the group is calculated by the
 * TilePageCalculator, at the time of writing the calculation uses a logarithmic relation to the number of tiles in the
 * zoom level.
 *
 * <p>The tile page is the atomic information handled by the disk quota subsystem, informations about frequency and
 * freshness of use is handled at this level, as such this is the minimum amount of tiles the disk quota subsystem
 * evicts when the
 *
 * @author Andrea Aime - GeoSolutions
 */
public class TilePage {

    private long id;

    private String tileSetId;

    private String key;

    private byte pageZ;

    private int pageY;

    private int pageX;

    private int creationTimeMinutes;

    public TilePage() {}

    public TilePage(String tileSetId, int pageX, int pageY, int zoomLevel, int creationTimeMinutes) {
        this.tileSetId = tileSetId;
        this.pageX = pageX;
        this.pageY = pageY;
        this.pageZ = (byte) zoomLevel;
        this.creationTimeMinutes = creationTimeMinutes;
        StringBuilder sb = new StringBuilder(128);
        computeId(tileSetId, pageX, pageY, zoomLevel, sb);
        this.key = sb.toString();
    }

    public TilePage(String tileSetId, int pageX, int pageY, int zoomLevel) {
        this(tileSetId, pageX, pageY, zoomLevel, SystemUtils.get().currentTimeMinutes());
    }

    public static void computeId(String tileSetId, int pageX, int pageY, int pageZ, StringBuilder target) {

        target.append(tileSetId)
                .append('@')
                .append(pageX)
                .append(',')
                .append(pageY)
                .append(',')
                .append(pageZ);
    }

    public long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TilePage)) {
            return false;
        }
        TilePage t = (TilePage) o;
        return key.equals(t.key);
    }

    @Override
    public int hashCode() {
        return 17 * key.hashCode();
    }

    public String getTileSetId() {
        return tileSetId;
    }

    public byte getZoomLevel() {
        return pageZ;
    }

    public int getPageY() {
        return pageY;
    }

    public int getPageX() {
        return pageX;
    }

    public int getCreationTimeMinutes() {
        return creationTimeMinutes;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append('[')
                .append(key)
                .append(']')
                .toString();
    }
}
