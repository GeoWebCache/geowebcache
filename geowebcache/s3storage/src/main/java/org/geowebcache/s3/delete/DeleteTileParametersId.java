package org.geowebcache.s3.delete;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DeleteTileParametersId implements DeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String gridSetId;
    private final String format;
    private final String parameterId;

    private final String layerName;

    private final String path;

    public DeleteTileParametersId(
            String prefix,
            String bucket,
            String layerId,
            String gridSetId,
            String format,
            String parametersId,
            String layerName) {
        checkNotNull(prefix, "Prefix must not be null");
        checkNotNull(bucket, "Bucket must not be null");
        checkNotNull(layerId, "LayerId must not be null");
        checkNotNull(gridSetId, "GridSetId must not be null");
        checkNotNull(format, "Format must not be null");
        checkNotNull(parametersId, "ParametersId must not be null");
        checkNotNull(layerName, "LayerName must not be null");

        checkArgument(!bucket.trim().isEmpty(), "Bucket must not be empty");
        checkArgument(!layerId.trim().isEmpty(), "LayerId must not be empty");
        checkArgument(!gridSetId.trim().isEmpty(), "GridSetId must not be empty");
        checkArgument(!format.trim().isEmpty(), "Format must not be empty");
        checkArgument(!parametersId.trim().isEmpty(), "ParametersId must not be empty");
        checkArgument(!layerName.trim().isEmpty(), "LayerName must not be empty");

        this.prefix = prefix.trim();
        this.bucket = bucket.trim();
        this.layerId = layerId.trim();
        this.gridSetId = gridSetId.trim();
        this.format = format.trim();
        this.parameterId = parametersId.trim();
        this.layerName = layerName.trim();

        this.path = DeleteTileInfo.toParametersId(prefix, layerId, gridSetId, format, parametersId);
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

    public String getLayerName() {
        return layerName;
    }

    public String getGridSetId() {
        return gridSetId;
    }

    public String getParameterId() {
        return parameterId;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getFormat() {
        return format;
    }
}
