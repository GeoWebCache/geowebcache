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

import com.google.common.annotations.VisibleForTesting;
import java.io.Serializable;
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

    private Resource blob;

    private String parameters_id = null;

    private long x;
    private long y;
    private int z;

    private String layer_name;

    private Map<String, String> parameters;

    private String gridSetId;

    public static TileObject createQueryTileObject(
            String layerName,
            long[] xyz,
            String gridSetId,
            String format,
            Map<String, String> parameters) {

        TileIndex index = xyz == null ? null : TileIndex.valueOf(xyz);

        return createQueryTileObject(layerName, index, gridSetId, format, parameters);
    }

    public static TileObject createQueryTileObject(
            String layerName,
            TileIndex xyz,
            String gridSetId,
            String format,
            Map<String, String> parameters) {

        TileObject obj = new TileObject();

        obj.layer_name = layerName;
        if (xyz != null) {
            obj.x = xyz.getX();
            obj.y = xyz.getY();
            obj.z = xyz.getZ();
        }
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

        TileIndex index = xyz == null ? null : TileIndex.valueOf(xyz);
        return createCompleteTileObject(layerName, index, gridSetId, format, parameters, blob);
    }

    public static TileObject createCompleteTileObject(
            String layerName,
            TileIndex xyz,
            String gridSetId,
            String format,
            Map<String, String> parameters,
            Resource blob) {

        TileObject obj = createQueryTileObject(layerName, xyz, gridSetId, format, parameters);

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

    public TileIndex getIndex() {
        return TileIndex.valueOf(x, y, z);
    }

    public long getX() {
        return x;
    }

    public long getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @VisibleForTesting
    public TileObject setX(long x) {
        this.x = x;
        return this;
    }

    @VisibleForTesting
    public TileObject setY(long y) {
        this.y = y;
        return this;
    }

    @VisibleForTesting
    public TileObject setZ(int z) {
        this.z = z;
        return this;
    }

    /**
     * To be deprecated, use individual methods {@link #getX()}, {@link #getY()}, {@link #getZ()}
     *
     * @return a newly allocated array with the x,y,z values
     */
    public long[] getXYZ() {
        return new long[] {x, y, z};
    }

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
        return String.format("[%s,%s,{%d,%d,%d}]", layer_name, gridSetId, x, y, z);
    }
}
