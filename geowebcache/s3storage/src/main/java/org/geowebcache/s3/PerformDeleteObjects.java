package org.geowebcache.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PerformDeleteObjects implements Function<DeleteObjectsRequest, DeleteObjectsResult> {
    private final AmazonS3Wrapper wrapper;
    private final BulkDeleteTask.Statistics.SubStats stats;

    public PerformDeleteObjects(AmazonS3Wrapper wrapper, BulkDeleteTask.Statistics.SubStats stats) {
        this.wrapper = wrapper;
        this.stats = stats;
    }

    @Override
    public DeleteObjectsResult apply(DeleteObjectsRequest deleteObjectsRequest) {
        try {
            DeleteObjectsResult deleteObjectsResult = wrapper.deleteObjects(deleteObjectsRequest);
            stats.updateBatches(deleteObjectsRequest.getKeys().size());
            return deleteObjectsResult;
        } catch (AmazonServiceException e) {
            S3BlobStore.log.severe(e.getMessage());
            switch (e.getErrorType()) {
                case Client:
                    stats.nonrecoverableIssues.add(e);
                    break;
                case Service:
                    stats.recoverableIssues.add(e);
                    break;
                case Unknown:
                    stats.unknownIssues.add(e);
                    break;
            }
            return new DeleteObjectsResult(new ArrayList<>());
        }
    }
}
