package org.geowebcache.s3;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.geowebcache.util.KeyObject;

public class MapKeyObjectsToDeleteObjectRequest implements Function<List<KeyObject>, DeleteObjectsRequest> {
    private final String bucket;

    public MapKeyObjectsToDeleteObjectRequest(String bucket) {
        this.bucket = bucket;
    }

    @Override
    public DeleteObjectsRequest apply(List<KeyObject> keyObjects) {
        var request = new DeleteObjectsRequest(bucket);
        var keys = keyObjects.stream()
                .map(KeyObject::objectPath)
                .map(DeleteObjectsRequest.KeyVersion::new)
                .collect(Collectors.toList());

        request.setKeys(keys);
        return request;
    }
}
