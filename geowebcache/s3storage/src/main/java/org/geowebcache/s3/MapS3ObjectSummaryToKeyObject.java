package org.geowebcache.s3;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.function.Function;
import org.geowebcache.util.KeyObject;

public class MapS3ObjectSummaryToKeyObject implements Function<S3ObjectSummary, KeyObject> {
    @Override
    public KeyObject apply(S3ObjectSummary s3ObjectSummary) {
        return KeyObject.fromObjectPath(s3ObjectSummary.getKey());
    }
}
