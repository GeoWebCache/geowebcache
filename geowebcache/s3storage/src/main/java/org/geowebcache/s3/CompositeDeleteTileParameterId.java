package org.geowebcache.s3;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.geowebcache.util.KeyObject;

class CompositeDeleteTileParameterId implements CompositeDeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String parameterId;
    private final String layerName;
    private final List<DeleteTileRange> children = new ArrayList<>();

    private final String path;

    public CompositeDeleteTileParameterId(
            String prefix,
            String bucket,
            String layerId,
            Set<String> gridSetIds,
            Set<String> formats,
            String parametersId,
            String layerName) {
        checkNotNull(prefix, "prefix must not be null");
        checkNotNull(bucket, "bucket cannot be null");
        checkNotNull(layerId, "layerId cannot be null");
        checkNotNull(gridSetIds, "gridSetIds cannot be null");
        checkNotNull(parametersId, "parametersId cannot be null");
        checkNotNull(layerName, "layerName cannot be null");
        checkArgument(!gridSetIds.isEmpty(), gridSetIds.toString());

        this.prefix = prefix;
        this.bucket = bucket;
        this.layerId = layerId;
        this.parameterId = parametersId;
        this.layerName = layerName;

        formats.forEach(format -> {
            gridSetIds.forEach(gridSetId -> {
                add(new DeleteTileParameterId(prefix, bucket, layerId, gridSetId, format, parametersId, layerName));
            });
        });

        this.path = KeyObject.toLayerId(prefix, layerId);
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

    public String getParameterId() {
        return parameterId;
    }

    public String getLayerName() {
        return layerName;
    }

    @Override
    public List<DeleteTileRange> children() {
        return new ArrayList<>(children);
    }

    @Override
    public void add(DeleteTileRange child) {
        checkNotNull(child, "child cannot be null");
        checkArgument(child instanceof DeleteTileGridSet, "child should be a DeleteTileGridSet");

        DeleteTileParameterId gridSet = (DeleteTileParameterId) child;

        checkArgument(gridSet.getBucket() == getBucket(), "child bucket should be the same as the bucket");
        checkArgument(gridSet.getLayerName() == getLayerName(), "child layer name should be the same as the layerName");
        checkArgument(gridSet.getLayerId() == getLayerId(), "child layer id should be the same as the layerId");
        checkArgument(
                gridSet.getParameterId() == getParameterId(),
                "child parameter id should be the same as the parameterId");

        children.add(child);
    }
}
