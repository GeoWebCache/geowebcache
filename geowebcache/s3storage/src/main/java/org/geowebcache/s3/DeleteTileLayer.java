package org.geowebcache.s3;

import org.geowebcache.util.KeyObject;

class DeleteTileLayer implements DeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String layerName;

    private final String path;

    public DeleteTileLayer(String prefix, String bucket, String layerId, String layerName) {
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
