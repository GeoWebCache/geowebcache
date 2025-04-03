package org.geowebcache.s3;

import com.google.common.base.Preconditions;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public interface DeleteTileRange {
    String prefix();
}

class DeleteTileLayer implements DeleteTileRange {
    private final String bucket;
    private final String layerId;
    private final String layerName;

    public DeleteTileLayer(String bucket, String layerId, String layerName) {
        this.bucket = bucket;
        this.layerId = layerId;
        this.layerName = layerName;
    }

    public String prefix() {
        return format("%s/", layerId);
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

    static Builder newBuilder() {
        return new Builder();
    }

    static class Builder{
        private String bucket;
        private String layerId;
        private String layerName;

        public Builder withBucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder withLayerId(String layer) {
            this.layerId = layer;
            return this;
        }

        public Builder withLayerName(String layerName) {
            this.layerName = layerName;
            return this;
        }

        public DeleteTileLayer build() {
            checkNotNull(bucket, "bucket cannot be null");
            checkNotNull(layerId, "layer cannot be null");
            checkNotNull(layerName, "layerName cannot be null");

            return new DeleteTileLayer(bucket, layerId, layerName);
        }
    }
}

class DeleteTileGridSet implements DeleteTileRange {
    private final String bucket;
    private final String layerId;
    private final String gridSetId;
    private final String layerName;

    public DeleteTileGridSet(String bucket, String layerId, String gridSetId, String layerName) {
        this.bucket = bucket;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.layerName = layerName;
    }

    public String prefix() {
        return format("%s/%s/", getLayerId(), getGridSetId());
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

    static Builder newBuilder() {
        return new Builder();
    }


    static class Builder{
        private String bucket;
        private String layerId;
        private String gridSetId;
        private String layerName;

        public Builder withBucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder withLayer(String layer) {
            this.layerId = layer;
            return this;
        }

        public Builder withGridSetId(String gridSetId) {
            this.gridSetId = gridSetId;
            return this;
        }

        public Builder withLayerName(String layerName) {
            this.layerName = layerName;
            return this;
        }

        public DeleteTileGridSet build() {
            checkNotNull(bucket, "bucket cannot be null");
            checkNotNull(layerId, "layer id cannot be null");
            checkNotNull(layerName, "layerName cannot be null");
            checkNotNull(gridSetId, "gridSetId cannot be null");

            return new DeleteTileGridSet(bucket, layerId, gridSetId, layerName);
        }
    }
}

class DeleteTileParameterId implements DeleteTileRange {
    private final String bucket;
    private final String layerId;
    private final String gridSetId;
    private final String parameterId;
    private final String layerName;

    public DeleteTileParameterId(String bucket, String layerId, String gridSetId, String parameterId, String layerName) {
        this.bucket = bucket;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.parameterId = parameterId;
        this.layerName = layerName;
    }

    public String prefix() {
        return format("%s/%s/%s/", layerId, gridSetId, parameterId);
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

    static Builder newBuilder() {
        return new Builder();
    }

    static class Builder{
        private String bucket;
        private String layerId;
        private String gridSetId;
        private String parameterId;
        private String layerName;

        public Builder withBucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder withLayer(String layer) {
            this.layerId = layer;
            return this;
        }

        public Builder withGridSetId(String gridSetId) {
            this.gridSetId = gridSetId;
            return this;
        }

        public Builder withParameterId(String parameterId) {
            this.parameterId = parameterId;
            return this;
        }

        public Builder withLayerName(String layerName) {
            this.layerName = layerName;
            return this;
        }

        public DeleteTileParameterId build() {
            return new DeleteTileParameterId(bucket, layerId, gridSetId, parameterId, layerName);
        }
    }
}

