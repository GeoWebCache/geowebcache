/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota.storage;

/**
 * Identifiers a group of tiles uniform by layer, gridset, format and eventual request parameters.
 *
 * @author Andrea Aime - GeoSolutions
 */
public class TileSet implements Comparable<TileSet> {

    private String key;

    private String layerName;

    private String gridsetId;

    private String blobFormat;

    private String parametersId;

    private transient int cachedHashCode;

    TileSet() {
        // empty constructor, needed by runtime code optimizers and reflection
    }

    public TileSet(String id) {
        this.key = id;
    }

    /**
     * @param layerName layer name, non null
     * @param gridsetId gridset id, non null
     * @param blobFormat blob format, non null
     * @param parametersId extra tileset scope identifier, may be null, indicating the default
     *     tileset for the given layer/gridset/format
     */
    public TileSet(String layerName, String gridsetId, String blobFormat, String parametersId) {
        this.layerName = layerName;
        this.gridsetId = gridsetId;
        this.blobFormat = blobFormat;
        this.parametersId = parametersId;
        StringBuilder sb = new StringBuilder(128);
        computeId(layerName, gridsetId, blobFormat, parametersId, sb);
        this.key = sb.toString();
    }

    public static void computeId(
            String layerName,
            String gridsetId,
            String blobFormat,
            String parametersId,
            StringBuilder idTarget) {
        idTarget.append(layerName).append('#').append(gridsetId).append('#').append(blobFormat);
        if (parametersId != null) {
            idTarget.append('#').append(parametersId);
        }
    }

    /**
     * Initializes the other fields of the tileset from an id with the
     * layer#gridset#format[#paramId] structure
     */
    public void initFromId() {
        String[] splitted = key.split("#");
        if (splitted.length < 3 || splitted.length > 4) {
            throw new IllegalArgumentException(
                    "Invalid key for standard tile set, "
                            + "it should have the layer#gridset#format[#paramId]");
        }

        this.layerName = splitted[0];
        this.gridsetId = splitted[1];
        this.blobFormat = splitted[2];
        if (splitted.length == 4) {
            this.parametersId = splitted[3];
        }
    }

    public String getId() {
        return key;
    }

    public String getLayerName() {
        return layerName;
    }

    public String getGridsetId() {
        return gridsetId;
    }

    public String getBlobFormat() {
        return blobFormat;
    }

    public String getParametersId() {
        return parametersId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TileSet)) {
            return false;
        }
        TileSet t = (TileSet) o;
        boolean equals = key.equals(t.getId());

        return equals;
    }

    @Override
    public int hashCode() {
        if (cachedHashCode == 0) {
            cachedHashCode = 17 * key.hashCode();
        }
        return cachedHashCode;
    }

    /** @see java.lang.Comparable#compareTo(java.lang.Object) */
    @Override
    public int compareTo(TileSet o) {
        int val = layerName.compareTo(o.layerName);
        if (val != 0) {
            return val;
        }
        val = gridsetId.compareTo(o.gridsetId);
        if (val != 0) {
            return val;
        }
        val = blobFormat.compareTo(o.blobFormat);
        if (val != 0) {
            return val;
        }

        if (parametersId == null) {
            return o.parametersId == null ? 0 : -1;
        } else if (o.parametersId == null) {
            return 1;
        } else {
            return parametersId.compareTo(o.parametersId);
        }
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append("[")
                .append(key)
                .append("]")
                .toString();
    }
}
