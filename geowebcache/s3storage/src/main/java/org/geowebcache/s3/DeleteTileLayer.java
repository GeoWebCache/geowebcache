package org.geowebcache.s3;

import org.geowebcache.util.KeyObject;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

class DeleteTileLayer implements DeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String layerName;

    private final String path;

    public DeleteTileLayer(String prefix, String bucket, String layerId, String layerName) {
        checkNotNull(prefix, "prefix cannot not be null");
        checkNotNull(bucket, "bucket cannot not be null");
        checkNotNull(layerId, "layerId cannot not be null");
        checkNotNull(layerName, "layerName cannot not be null");
        checkArgument(!bucket.isBlank(), "bucket cannot be blank");
        checkArgument(!layerId.isBlank(), "layerId cannot be blank");
        checkArgument(!layerName.isBlank(), "layerName cannot be blank");

        this.prefix = prefix;
        this.bucket = bucket;
        this.layerId = layerId;
        this.layerName = layerName;
        this.path = KeyObject.toLayerId(prefix, layerId);
    }

    public String getPrefix() {
        return prefix;
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
}
