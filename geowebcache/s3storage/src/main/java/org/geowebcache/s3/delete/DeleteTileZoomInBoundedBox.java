package org.geowebcache.s3.delete;

import org.geowebcache.storage.TileRange;

public class DeleteTileZoomInBoundedBox implements DeleteTileRangeWithTileRange {

    private final String prefix;
    private final String bucketName;
    private final String layerId;
    private final String gridSetId;
    private final String format;
    private final String parametersId;
    private final long zoomLevel;
    private final long[] boundedBox;
    private final TileRange tileRange;
    private final int[] metaTilingFactor;

    private final String path;

    public DeleteTileZoomInBoundedBox(
            String prefix,
            String bucketName,
            String layerId,
            String gridSetId,
            String format,
            String parametersId,
            long zoomLevel,
            long[] boundedBox,
            TileRange tileRange,
            int[] metaTilingFactor) {
        this.prefix = prefix;
        this.bucketName = bucketName;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.format = format;
        this.parametersId = parametersId;
        this.zoomLevel = zoomLevel;
        this.boundedBox = boundedBox;
        this.tileRange = tileRange;
        this.metaTilingFactor = metaTilingFactor;

        this.path = DeleteTileInfo.toZoomPrefix(prefix, layerId, gridSetId, format, parametersId, zoomLevel);
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    public String getBucketName() {
        return bucketName;
    }

    @Override
    public String getLayerId() {
        return layerId;
    }

    @Override
    public String getGridSetId() {
        return gridSetId;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public String getParametersId() {
        return parametersId;
    }

    public long getZoomLevel() {
        return zoomLevel;
    }

    public long[] getBoundedBox() {
        return boundedBox;
    }

    @Override
    public TileRange getTileRange() {
        return tileRange;
    }

    @Override
    public int[] getMetaTilingFactor() {
        return metaTilingFactor;
    }
}
