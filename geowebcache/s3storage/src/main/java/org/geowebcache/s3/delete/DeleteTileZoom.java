package org.geowebcache.s3.delete;

public class DeleteTileZoom implements DeleteTileRange {
    private final String prefix;
    private final String bucketName;
    private final String layerId;
    private final String gridSetId;
    private final String format;
    private final String paramatesId;
    private final long zoomLevel;

    private final String path;

    public DeleteTileZoom(
            String prefix,
            String bucketName,
            String layerId,
            String gridSetId,
            String format,
            String paramatesId,
            long zoomLevel) {
        this.prefix = prefix;
        this.bucketName = bucketName;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.format = format;
        this.paramatesId = paramatesId;
        this.zoomLevel = zoomLevel;

        this.path = DeleteTileInfo.toZoomPrefix(prefix, layerId, gridSetId, format, paramatesId, zoomLevel);
    }

    @Override
    public String path() {
        return path;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getBucketName() {
        return bucketName;
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

    public String getParamatesId() {
        return paramatesId;
    }

    public long getZoomLevel() {
        return zoomLevel;
    }
}
