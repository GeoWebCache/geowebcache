package org.geowebcache.diskquota.storage;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKey;

@Entity(version=1)
public class TileSet implements Comparable<TileSet> {

    @PrimaryKey
    private String key;

    @SecondaryKey(name = "layer", relate = Relationship.MANY_TO_ONE)
    private String layerName;

    private String gridsetId;

    private String blobFormat;

    private String parametersId;

    private transient int cachedHashCode;

    TileSet() {
        // empty constructor, needed by runtime code optimizers and reflection
    }

    TileSet(String id) {
        this.key = id;
    }

    /**
     * 
     * @param layerName
     *            layer name, non null
     * @param gridsetId
     *            gridset id, non null
     * @param blobFormat
     *            blob format, non null
     * @param parametersId
     *            extra tileset scope identifier, may be null, indicating the default tileset for
     *            the given layer/gridset/format
     * @param size
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

    public static void computeId(String layerName, String gridsetId, String blobFormat,
            String parametersId, StringBuilder idTarget) {
        idTarget.append(layerName).append('#').append(gridsetId).append('#').append(blobFormat);
        if (parametersId != null) {
            idTarget.append('#').append(parametersId);
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

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
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
        
        if(parametersId == null) {
            return o.parametersId == null ? 0 : -1;
        } else if(o.parametersId == null) {
            return 1;
        } else {
            return parametersId.compareTo(o.parametersId);
        }
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append("[").append(key).append("]")
                .toString();
    }

}
