package org.geowebcache.s3.streams;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.geowebcache.s3.delete.DeleteTileInfo;

import java.util.function.Function;

public class MapS3ObjectSummaryToKeyObject implements Function<S3ObjectSummary, DeleteTileInfo> {
    @Override
    public DeleteTileInfo apply(S3ObjectSummary s3ObjectSummary) {
        return DeleteTileInfo.fromObjectPath(s3ObjectSummary.getKey());
    }
}
