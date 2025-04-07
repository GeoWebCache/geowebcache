package org.geowebcache.s3.delete;

public class DeleteTileGridSet implements DeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String gridSetId;
    private final String layerName;

    private final String path;

    public DeleteTileGridSet(String prefix, String bucket, String layerId, String gridSetId, String layerName) {
        this.prefix = prefix;
        this.bucket = bucket;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.layerName = layerName;

        this.path = DeleteTileInfo.toGridSet(prefix, layerId, gridSetId);
    }

    public String path() {
        return path;
    }

    public String getBucket() {
        return bucket;
    }

    public String getLayerId() {
        return layerId;
    }

    public String getGridSetId() {
        return gridSetId;
    }

    public String getLayerName() {
        return layerName;
    }

    public String getPrefix() {
        return prefix;
    }
}
