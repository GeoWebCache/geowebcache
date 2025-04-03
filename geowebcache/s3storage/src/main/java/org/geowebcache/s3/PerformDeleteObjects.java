package org.geowebcache.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PerformDeleteObjects implements Function<DeleteObjectsRequest, DeleteObjectsResult> {
    private final AmazonS3Wrapper wrapper;
    private final List<AmazonServiceException> issues = new ArrayList<>();

    public PerformDeleteObjects(AmazonS3Wrapper wrapper) {
        this.wrapper = wrapper;
    }

    public List<AmazonServiceException> getIssues() {
        return issues;
    }

    @Override
    public DeleteObjectsResult apply(DeleteObjectsRequest deleteObjectsRequest) {
        try {
            return wrapper.deleteObjects(deleteObjectsRequest);
        } catch (AmazonServiceException e) {
            // TODO Errors that retryable type = Service should be retried
            S3BlobStore.log.severe(e.getMessage());
            issues.add(e);
            return new DeleteObjectsResult(new ArrayList<>());
        }
    }
}
