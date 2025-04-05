package org.geowebcache.s3.delete;


public class DeleteTilePrefix implements DeleteTileRange{
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String gridSetId;
    private final String format;
    private final String parameterId;
    private final String path;

    public DeleteTilePrefix(String prefix, String bucket, String layerId, String gridSetId, String format, String parameterId, String path) {
        this.prefix = prefix;
        this.bucket = bucket;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.format = format;
        this.parameterId = parameterId;
        this.path = path;
    }

    @Override
    public String path() {
        return path;
    }

    public String getPrefix() {
        return prefix;
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

    public String getFormat() {
        return format;
    }

    public String getParameterId() {
        return parameterId;
    }
}
