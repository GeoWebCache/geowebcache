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
 * @author Arne Kepp / The Open Planning Project 2009
 */
package org.geowebcache.storage;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import org.geowebcache.grid.GridSet;
import org.geowebcache.io.Resource;

/**
 * Represents a specific tile in a {@link GridSet}, identified by xyz. Normally the contents can be
 * either an image or a regionated KML tile, the class is format agnostic and carries a {@link
 * Resource} object with the data.
 */
public class TileObject extends StorageObject implements Serializable {
    /** serialVersionUID */
    private static final long serialVersionUID = 2204318806003485110L;

    public static final String TYPE = "tile";

    Resource blob;

    String parameters_id = null;

    long[] xyz;

    String layer_name;

    Map<String, String> parameters;

    String gridSetId;

    public static TileObject createQueryTileObject(
            String layerName,
            long[] xyz,
            String gridSetId,
            String format,
            Map<String, String> parameters) {
        TileObject obj = new TileObject();

        obj.layer_name = layerName;
        obj.xyz = xyz;
        obj.gridSetId = gridSetId;
        obj.blob_format = format;
        obj.parameters = parameters;

        return obj;
    }

    public static TileObject createCompleteTileObject(
            String layerName,
            long[] xyz,
            String gridSetId,
            String format,
            Map<String, String> parameters,
            Resource blob) {
        TileObject obj = new TileObject();

        obj.layer_name = layerName;
        obj.xyz = xyz;
        obj.gridSetId = gridSetId;
        obj.blob_format = format;
        obj.parameters = parameters;

        if (blob == null) {
            obj.blob_size = -1;
        } else {
            obj.blob_size = (int) blob.getSize();
            obj.blob = blob;
        }

        obj.created = System.currentTimeMillis();
        return obj;
    }

    private TileObject() {}

    public Resource getBlob() {
        return blob;
    }

    public void setBlob(Resource blob) {
        if (blob != null) {
            this.blob_size = (int) blob.getSize();
        } else {
            this.blob_size = -1;
        }

        this.blob = blob;
    }

    public String getGridSetId() {
        return this.gridSetId;
    }

    /** May be null until this object has been handled by the BlobStore */
    public String getParametersId() {
        return this.parameters_id;
    }

    /**
     * The BlobStore is responsible for setting this based on the value of {@link #getParameters()}
     */
    public void setParametersId(String parameters_id) {
        this.parameters_id = parameters_id;
    }

    public long[] getXYZ() {
        return xyz;
    }

    // public int getSrs() {
    // return srs;
    // }

    public String getLayerName() {
        return layer_name;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "[" + layer_name + "," + gridSetId + ",{" + Arrays.toString(xyz) + "}]";
    }
}
