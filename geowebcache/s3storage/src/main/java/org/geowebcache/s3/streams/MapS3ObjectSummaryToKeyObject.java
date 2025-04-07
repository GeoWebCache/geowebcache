package org.geowebcache.s3.streams;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.function.Function;
import org.geowebcache.s3.delete.DeleteTileInfo;

public class MapS3ObjectSummaryToKeyObject implements Function<S3ObjectSummary, DeleteTileInfo> {
    @Override
    public DeleteTileInfo apply(S3ObjectSummary s3ObjectSummary) {
        DeleteTileInfo info = DeleteTileInfo.fromObjectPath(s3ObjectSummary.getKey());
        info.setSize(s3ObjectSummary.getSize());
        return info;
    }
}
