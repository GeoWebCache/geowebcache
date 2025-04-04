package org.geowebcache.s3;

import org.geowebcache.storage.TileRange;
import org.geowebcache.util.KeyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.google.common.base.Preconditions.checkNotNull;

public class CompositeDeleteTilesInRange implements CompositeDeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String layerId;
    private final String format;
    private final TileRange tileRange;

    private final String path;
    private final List<DeleteTileRange> deleteTileRanges;

    public CompositeDeleteTilesInRange(String prefix, String bucket, String layerId, String format, TileRange tileRange) {
        checkNotNull(tileRange, "tilerange must not be null");
        checkNotNull(prefix, "prefix must not be null");
        checkNotNull(layerId, "layerId must not be null");
        checkNotNull(format, "format must not be null");
        checkNotNull(bucket, "bucket must not be null");

        this.prefix = prefix;
        this.bucket = bucket;
        this.layerId = layerId;
        this.format = format;
        this.tileRange = tileRange;

        this.path = KeyObject.toParametersId(this.prefix, this.layerId, tileRange.getGridSetId(), this.format, tileRange.getParametersId());

        this.deleteTileRanges = LongStream.of(tileRange.getZoomStart(), tileRange.getZoomStop()).mapToObj(zoomLevel -> {
            long[] bounds = tileRange.rangeBounds((int)zoomLevel);
            if (bounds != null && bounds.length >= 4) {
                return new DeleteTilesByZoomLevelInBoundedBox(prefix, bucket, layerId, tileRange.getGridSetId(), format, tileRange.getParametersId(), zoomLevel, bounds);
            } else {
                return new DeleteTilesByZoomLevel(prefix, bucket, layerId, tileRange.getGridSetId(), format, tileRange.getParametersId(), zoomLevel);
            }
        }).collect(Collectors.toList());

    }

    @Override
    public List<DeleteTileRange> children() {
        return new ArrayList<>(deleteTileRanges);
    }

    @Override
    public void add(DeleteTileRange child) {

    }

    @Override
    public String path() {
        return path;
    }
}
