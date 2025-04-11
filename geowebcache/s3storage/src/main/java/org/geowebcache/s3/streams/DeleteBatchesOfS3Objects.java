package org.geowebcache.s3.streams;

import static java.util.stream.Collectors.toMap;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DeleteBatchesOfS3Objects<T> implements Function<List<T>, List<T>> {
    private final String bucket;
    private final AmazonS3 conn;
    private final Function<T, String> mapToKeyPath;
    private final Logger logger;

    public DeleteBatchesOfS3Objects(String bucket, AmazonS3 conn, Function<T, String> mapToKeyPath, Logger logger) {
        this.bucket = bucket;
        this.conn = conn;
        this.mapToKeyPath = mapToKeyPath;
        this.logger = logger;
    }

    @Override
    public List<T> apply(List<T> objectList) {
        if (!objectList.isEmpty()) {
            Map<String, T> tilesByPath = makeMapOfTilesByPath(objectList);
            DeleteObjectsRequest deleteObjectsRequest = buildRequest(tilesByPath);
            DeleteObjectsResult deleteObjectsResult = makeRequest(deleteObjectsRequest);
            return collectResults(deleteObjectsResult, tilesByPath);
        } else {
            logger.info("Expected a batch of object to delete received none");
            return List.of();
        }
    }

    private List<T> collectResults(DeleteObjectsResult deleteObjectsResult, Map<String, T> tilesByPath) {
        return deleteObjectsResult.getDeletedObjects().stream()
                .map(deletedObject -> tilesByPath.get(deletedObject.getKey()))
                .collect(Collectors.toList());
    }

    private DeleteObjectsResult makeRequest(DeleteObjectsRequest deleteObjectsRequest) {
        try {
            return conn.deleteObjects(deleteObjectsRequest);
        } catch (AmazonServiceException e) {
            return new DeleteObjectsResult(new ArrayList<>());
        }
    }

    private DeleteObjectsRequest buildRequest(Map<String, T> tilesByPath) {
        DeleteObjectsRequest request = new DeleteObjectsRequest(bucket);
        request.setBucketName(bucket);
        request.setKeys(tilesByPath.keySet().stream().map(KeyVersion::new).collect(Collectors.toList()));
        request.setQuiet(false);
        return request;
    }

    private Map<String, T> makeMapOfTilesByPath(List<T> tileList) {
        return tileList.stream().collect(toMap(mapToKeyPath, Function.identity()));
    }
}
