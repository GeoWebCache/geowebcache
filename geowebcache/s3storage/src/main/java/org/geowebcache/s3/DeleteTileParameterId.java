package org.geowebcache.s3;

import static java.lang.String.format;

import org.geowebcache.util.KeyObject;

class DeleteTileParameterId implements DeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String gridSetId;
    private final String format;
    private final String parameterId;
    private final String layerName;

    private final String path;

    public DeleteTileParameterId(
            String prefix,
            String bucket,
            String layerId,
            String gridSetId,
            String format,
            String parametersId,
            String layerName) {
        this.prefix = prefix;
        this.bucket = bucket;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.format = format;
        this.parameterId = parametersId;
        this.layerName = layerName;

        this.path = KeyObject.toParametersId(prefix, layerId, gridSetId, format, parametersId);
    }

    public String path() {
        return format("%s/%s/%s/%s/", layerId, gridSetId, format, parameterId);
    }

    public String getBucket() {
        return bucket;
    }

    public String getLayerId() {
        return layerId;
    }

    public String getLayerName() {
        return layerId;
    }

    public String getGridSetId() {
        return gridSetId;
    }

    public String getParameterId() {
        return parameterId;
    }
}
