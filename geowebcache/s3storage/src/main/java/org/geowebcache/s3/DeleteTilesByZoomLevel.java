package org.geowebcache.s3;

import org.geowebcache.util.KeyObject;

public class DeleteTilesByZoomLevel implements DeleteTileRange {
    private final String prefix;
    private final String bucketName;
    private final String layerId;
    private final String gridSetId;
    private final String format;
    private final String paramatesId;
    private final long zoomLevel;

    private final String path;


    public DeleteTilesByZoomLevel(String prefix, String bucketName, String layerId, String gridSetId, String format, String paramatesId, long zoomLevel) {
        this.prefix = prefix;
        this.bucketName = bucketName;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.format = format;
        this.paramatesId = paramatesId;
        this.zoomLevel = zoomLevel;

        this.path = KeyObject.toZoomPrefix(prefix, layerId, gridSetId, format, paramatesId, zoomLevel);
    }

    @Override
    public String path() {
        return path;
    }
}
