package org.geowebcache.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import org.geowebcache.s3.BulkDeleteTask.BatchStats;
import org.geowebcache.s3.BulkDeleteTask.Callback;
import org.geowebcache.s3.BulkDeleteTask.Statistics.SubStats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

public class PerformDeleteObjects implements ToLongFunction<Map<String, DeleteTileInfo>> {
    private final AmazonS3Wrapper wrapper;
    private final SubStats stats;
    private final String bucket;
    private final Callback callback;
    private final DeleteTileRange deleteTileRange;

    public PerformDeleteObjects(AmazonS3Wrapper wrapper, String bucket, Callback callback, SubStats stats, DeleteTileRange deleteTileRange) {
        this.wrapper = wrapper;
        this.bucket = bucket;
        this.stats = stats;
        this.callback = callback;
        this.deleteTileRange = deleteTileRange;
    }

    @Override
    public long applyAsLong(Map<String, DeleteTileInfo> mapKeyObjectsByPath) {
        BatchStats batchStats = new BatchStats(deleteTileRange);

        callback.batchStarted(batchStats);
        DeleteObjectsRequest deleteObjectsRequest = mapKeyObjectsToDeleteObjectRequest(mapKeyObjectsByPath.values());
        DeleteObjectsResult deleteObjectsResult = makeRequest(deleteObjectsRequest);
        processResults(deleteObjectsResult, mapKeyObjectsByPath, batchStats);

        batchStats.setProcessed(mapKeyObjectsByPath.size());
        callback.batchEnded();
        return batchStats.processed;
    }

    private DeleteObjectsResult makeRequest(DeleteObjectsRequest deleteObjectsRequest) {
        try {
            return wrapper.deleteObjects(deleteObjectsRequest);
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

    public DeleteObjectsRequest mapKeyObjectsToDeleteObjectRequest(Collection<DeleteTileInfo> keyObjects) {
        var request = new DeleteObjectsRequest(bucket);
        var keys = keyObjects.stream()
                .map(DeleteTileInfo::objectPath)
                .map(DeleteObjectsRequest.KeyVersion::new)
                .collect(Collectors.toList());

        request.setBucketName(bucket);
        request.setKeys(keys);
        request.setQuiet(false); // TODO check this setting
        return request;
    }

    public void processResults(DeleteObjectsResult deleteObjectsResult, Map<String, DeleteTileInfo> mapKeyObjectsByPath, BatchStats batchStats) {
        deleteObjectsResult.getDeletedObjects().forEach(deletedObject -> {
                    DeleteTileInfo keyObject = mapKeyObjectsByPath.get(deletedObject.getKey());
                    BulkDeleteTask.ResultStat resultStat = new BulkDeleteTask.ResultStat(
                            deletedObject.getKey(),
                            keyObject.getTile(),
                            keyObject.getSize(),
                            Instant.now().getEpochSecond());
                    callback.tileDeleted(resultStat);
                }
        );
    }

}
