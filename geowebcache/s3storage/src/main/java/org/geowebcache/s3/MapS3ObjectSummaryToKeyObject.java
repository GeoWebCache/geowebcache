package org.geowebcache.s3;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.function.Function;

public class MapS3ObjectSummaryToKeyObject implements Function<S3ObjectSummary, DeleteTileInfo> {
    @Override
    public DeleteTileInfo apply(S3ObjectSummary s3ObjectSummary) {
        return DeleteTileInfo.fromObjectPath(s3ObjectSummary.getKey());
    }
}
