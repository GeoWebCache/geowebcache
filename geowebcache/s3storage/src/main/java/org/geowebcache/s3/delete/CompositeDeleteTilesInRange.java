package org.geowebcache.s3.delete;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.geowebcache.s3.delete.DeleteTileRangeWithTileRange.ONE_BY_ONE_META_TILING_FACTOR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.geowebcache.storage.TileRange;

public class CompositeDeleteTilesInRange implements CompositeDeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String format;
    private final TileRange tileRange;

    private final String path;
    private final List<DeleteTileRange> deleteTileRanges;

    public CompositeDeleteTilesInRange(
            String prefix, String bucket, String layerId, String format, TileRange tileRange) {
        checkNotNull(tileRange, "tile range must not be null");
        checkNotNull(prefix, "prefix must not be null");
        checkNotNull(bucket, "bucket must not be null");
        checkNotNull(layerId, "layerId must not be null");
        checkNotNull(format, "format must not be null");
        checkArgument(!bucket.trim().isEmpty(), "bucket must not be empty");
        checkArgument(!layerId.trim().isEmpty(), "layerId must not be empty");
        checkArgument(!format.trim().isEmpty(), "format must not be empty");

        this.prefix = prefix.trim();
        this.bucket = bucket.trim();
        this.layerId = layerId;
        this.format = format;
        this.tileRange = tileRange;

        this.path = DeleteTileInfo.toParametersId(
                this.prefix, this.layerId, tileRange.getGridSetId(), this.format, tileRange.getParametersId());

        this.deleteTileRanges = LongStream.range(tileRange.getZoomStart(), tileRange.getZoomStop()+1)
                .mapToObj(zoomLevel -> {
                    long[] bounds = tileRange.rangeBounds((int) zoomLevel);
                    if (bounds != null && bounds.length == 5) {
                        return new DeleteTileZoomInBoundedBox(
                                prefix,
                                bucket,
                                layerId,
                                tileRange.getGridSetId(),
                                format,
                                tileRange.getParametersId(),
                                zoomLevel,
                                bounds,
                                tileRange,
                                ONE_BY_ONE_META_TILING_FACTOR
                        );
                    } else {
                        return new DeleteTileZoom(
                                prefix,
                                bucket,
                                layerId,
                                tileRange.getGridSetId(),
                                format,
                                tileRange.getParametersId(),
                                zoomLevel,
                                tileRange
                        );
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<DeleteTileRange> children() {
        return new ArrayList<>(deleteTileRanges);
    }

    @Override
    public void add(DeleteTileRange child) {}

    @Override
    public String path() {
        return path;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getBucket() {
        return bucket;
    }

    public String getLayerId() {
        return layerId;
    }

    public String getFormat() {
        return format;
    }

    public TileRange getTileRange() {
        return tileRange;
    }
}
