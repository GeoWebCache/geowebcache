package org.geowebcache.s3.delete;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class CompositeDeleteTileParameterId implements CompositeDeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String parametersId;
    private final String layerName;
    private final List<DeleteTileParametersId> children = new ArrayList<>();

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
        checkArgument(!layerName.trim().isEmpty(), "layerName cannot be empty");
        checkArgument(!layerId.trim().isEmpty(), "layerId cannot be empty");
        checkArgument(!gridSetIds.isEmpty(), "gridSetIds cannot be empty");
        checkArgument(!formats.isEmpty(), "formats cannot be empty");
        checkArgument(!parametersId.trim().isEmpty(), "parametersId cannot be empty");
        checkArgument(!bucket.trim().isEmpty(), "bucket cannot be empty");

        this.prefix = prefix.trim();
        this.bucket = bucket.trim();
        this.layerId = layerId.trim();
        this.parametersId = parametersId.trim();
        this.layerName = layerName.trim();

        this.path = DeleteTileInfo.toLayerId(prefix, layerId);

        formats.forEach(format -> gridSetIds.forEach(gridSetId -> add(new DeleteTileParametersId(
                this.prefix, this.bucket, this.layerId, gridSetId, format, this.parametersId, this.layerName))));
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

    public String getParametersId() {
        return parametersId;
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
        checkArgument(child instanceof DeleteTileParametersId, "child should be a DeleteTileParameterId");

        DeleteTileParametersId parametersId = (DeleteTileParametersId) child;

        checkArgument(
                Objects.equals(parametersId.getBucket(), getBucket()), "child bucket should be the same as the bucket");
        checkArgument(
                Objects.equals(parametersId.getLayerName(), getLayerName()),
                "child layer name should be the same as the layerName");
        checkArgument(
                Objects.equals(parametersId.getLayerId(), getLayerId()),
                "child layer id should be the same as the layerId");
        checkArgument(
                Objects.equals(parametersId.getParameterId(), getParametersId()),
                "child parameter id should be the same as the parameterId");

        checkArgument(
                childMatchedExistingWithSameGridSetIdAndFormat(parametersId),
                "Already child with format and gridSetId");

        children.add(parametersId);
    }

    private boolean childMatchedExistingWithSameGridSetIdAndFormat(DeleteTileParametersId parametersId) {
        return children.stream()
                .noneMatch(elem -> Objects.equals(elem.getGridSetId(), parametersId.getGridSetId())
                        && Objects.equals(elem.getFormat(), parametersId.getFormat()));
    }
}
