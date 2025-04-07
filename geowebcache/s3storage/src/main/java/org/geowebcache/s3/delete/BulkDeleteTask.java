package org.geowebcache.s3.delete;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.geowebcache.s3.AmazonS3Wrapper;
import org.geowebcache.s3.S3BlobStore;
import org.geowebcache.s3.S3ObjectsWrapper;
import org.geowebcache.s3.callback.Callback;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;
import org.geowebcache.s3.streams.*;

public class BulkDeleteTask implements Callable<Long> {
    private final AmazonS3Wrapper amazonS3Wrapper;
    private final S3ObjectsWrapper s3ObjectsWrapper;
    private final String bucketName;
    private final DeleteTileRange deleteTileRange;
    private final int batch;

    private final Callback callback;

    // private final ThreadNotInterruptedPredicate threadNotInterrupted = new ThreadNotInterruptedPredicate();
    private final MapS3ObjectSummaryToKeyObject mapS3ObjectSummaryToKeyObject = new MapS3ObjectSummaryToKeyObject();
    private final MapKeyObjectsToDeleteObjectRequest mapKeyObjectsToDeleteObjectRequest =
            new MapKeyObjectsToDeleteObjectRequest();

    // Only build with builder
    private BulkDeleteTask(
            AmazonS3Wrapper amazonS3Wrapper,
            S3ObjectsWrapper s3ObjectsWrapper,
            String bucketName,
            DeleteTileRange deleteTileRange,
            Callback callback,
            int batch) {
        this.amazonS3Wrapper = amazonS3Wrapper;
        this.s3ObjectsWrapper = s3ObjectsWrapper;
        this.bucketName = bucketName;
        this.deleteTileRange = deleteTileRange;
        this.batch = batch;
        this.callback = callback;
    }

    public AmazonS3Wrapper getAmazonS3Wrapper() {
        return amazonS3Wrapper;
    }

    public S3ObjectsWrapper getS3ObjectsWrapper() {
        return s3ObjectsWrapper;
    }

    public String getBucketName() {
        return bucketName;
    }

    public DeleteTileRange getDeleteTileRange() {
        return deleteTileRange;
    }

    public int getBatch() {
        return batch;
    }

    public Callback getCallback() {
        return callback;
    }

    @Override
    public Long call() {
        Statistics statistics = new Statistics(deleteTileRange);
        callback.taskStarted(statistics);

        try {
            return deleteTileRange.stream()
                    .mapToLong(this::performDeleteStrategy)
                    .sum();

        } catch (Exception e) {
            S3BlobStore.getLog().severe(format("Exiting from bulk delete task: %s", e.getMessage()));
            statistics.addUnknownIssue(e);
            return statistics.getProcessed();
        } finally {
            callback.taskEnded();
        }
    }

    private Long performDeleteStrategy(DeleteTileRange deleteRange) {
        switch (chooseStrategy(deleteRange)) {
            case NoDeletionsRequired:
                return noDeletionsRequired(deleteRange);
            case SingleTile:
                return singleTile(deleteRange);
            case S3ObjectPathsForPrefix:
                return s3ObjectPathsForPrefix(deleteRange);
            case S3ObjectPathsForPrefixFilterByBoundedBox:
                return s3ObjectPathsForPrefixFilterByBoundedBox(deleteRange);
            case TileRangeWithBoundedBox:
                return tileRangeWithBounderBox((DeleteTileRangeWithTileRange) deleteRange);
            case TileRangeWithBoundedBoxIfTileExist:
                return tileRangeWithBounderBoxIfTileExists((DeleteTileRangeWithTileRange) deleteRange);
            default:
                return s3ObjectPathsForPrefix(deleteTileRange);
        }
    }

    private Long singleTile(DeleteTileRange deleteRange) {
        SubStats subStats = new SubStats(deleteTileRange, SingleTile);
        callback.subTaskStarted(subStats);

        PerformDeleteObjects performDeleteObjects =
                new PerformDeleteObjects(amazonS3Wrapper, bucketName, callback, subStats, deleteTileRange);

        S3BlobStore.getLog()
                .info(format(
                        "Using strategy SingleTile to a delete tile from bucket %s with prefix: %s",
                        bucketName, deleteTileRange.path()));

        Long count = batchedStreamOfKeyObjects(deleteTileRange, subStats)
                .map(mapKeyObjectsToDeleteObjectRequest)
                .mapToLong(performDeleteObjects)
                .sum();

        S3BlobStore.getLog()
                .info(format(
                        "Finished applying strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s processed: %d",
                        bucketName, deleteTileRange.path(), count));

        callback.subTaskEnded();
        return subStats.getProcessed();
    }

    private Long tileRangeWithBounderBox(DeleteTileRangeWithTileRange deleteTileRange) {
        S3BlobStore.getLog().warning("Strategy TileRangeWithBounderBox not implemented");
        SubStats subStats = new SubStats(deleteTileRange, TileRangeWithBoundedBox);
        callback.subTaskStarted(subStats);
        callback.subTaskEnded();
        return subStats.getProcessed();
    }

    private Long tileRangeWithBounderBoxIfTileExists(DeleteTileRangeWithTileRange deleteTileRange) {
        S3BlobStore.getLog().warning("Strategy TileRangeWithBounderBoxIfTileExists not implemented");
        SubStats subStats = new SubStats(deleteTileRange, TileRangeWithBoundedBoxIfTileExist);
        callback.subTaskStarted(subStats);

        var performDeleteObjects =
                new PerformDeleteObjects(amazonS3Wrapper, bucketName, callback, subStats, deleteTileRange);

        TileIteratorSupplier supplier = new TileIteratorSupplier(
                new TileIterator(
                        deleteTileRange.getTileRange(),
                        deleteTileRange.getMetaTilingFactor())
                , deleteTileRange
        );

        long count = BatchingIterator.batchedStreamOf(
                        Stream.generate(supplier)
                                .takeWhile(Objects::nonNull)
                        , batch)
                .map(mapKeyObjectsToDeleteObjectRequest)
                .mapToLong(performDeleteObjects)
                .sum();

        if (count != subStats.getDeleted()) {
            S3BlobStore.getLog()
                    .warning(format("Mismatch during tile delete expected %d found %d", count, subStats.getDeleted()));
        }

        callback.subTaskEnded();
        return subStats.getProcessed();
    }

    private Long s3ObjectPathsForPrefixFilterByBoundedBox(DeleteTileRange deleteTileRange) {
        S3BlobStore.getLog().warning("Strategy S3ObjectPathsForPrefixFilterByBoundedBox not implemented");
        SubStats subStats = new SubStats(deleteTileRange, S3ObjectPathsForPrefixFilterByBoundedBox);
        callback.subTaskStarted(subStats);
        callback.subTaskEnded();
        return subStats.getProcessed();
    }

    private Long noDeletionsRequired(DeleteTileRange deleteTileRange) {
        S3BlobStore.getLog().warning("Strategy NoDeletionsRequired nothing to do");
        SubStats subStats = new SubStats(deleteTileRange, NoDeletionsRequired);
        callback.subTaskStarted(subStats);
        callback.subTaskEnded();
        return subStats.getProcessed();
    }

    private Long s3ObjectPathsForPrefix(DeleteTileRange deleteTileRange) {
        SubStats subStats = new SubStats(deleteTileRange, S3ObjectPathsForPrefix);
        callback.subTaskStarted(subStats);

        var performDeleteObjects =
                new PerformDeleteObjects(amazonS3Wrapper, bucketName, callback, subStats, deleteTileRange);

        S3BlobStore.getLog()
                .info(format(
                        "Using strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s",
                        bucketName, deleteTileRange.path()));

        var count = batchedStreamOfKeyObjects(deleteTileRange, subStats)
                .map(mapKeyObjectsToDeleteObjectRequest)
                .mapToLong(performDeleteObjects)
                .sum();

        if (count != subStats.getDeleted()) {
            S3BlobStore.getLog()
                    .warning(format("Mismatch during tile delete expected %d found %d", count, subStats.getDeleted()));
        }

        S3BlobStore.getLog()
                .info(format(
                        "Finished applying strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s deleted: %d",
                        bucketName, deleteTileRange.path(), count));

        callback.subTaskEnded();
        return subStats.getProcessed();
    }

    private Stream<List<DeleteTileInfo>> batchedStreamOfKeyObjects(DeleteTileRange deleteTileRange, SubStats stats) {
        return BatchingIterator.batchedStreamOf(
                generateStreamOfKeyObjects(createS3ObjectPathsForPrefixSupplier(deleteTileRange.path()), stats), batch);
    }

    private Stream<DeleteTileInfo> generateStreamOfKeyObjects(
            S3ObjectPathsForPrefixSupplier supplier, SubStats subStats) {
        return Stream.generate(supplier).takeWhile(Objects::nonNull).map(mapS3ObjectSummaryToKeyObject);
    }

    private S3ObjectPathsForPrefixSupplier createS3ObjectPathsForPrefixSupplier(String prefix) {
        return S3ObjectPathsForPrefixSupplier.newBuilder()
                .withBucket(bucketName)
                .withPrefix(prefix)
                .withWrapper(s3ObjectsWrapper)
                .build();
    }

    ObjectPathStrategy chooseStrategy(DeleteTileRange deleteTileRange) {
        if (deleteTileRange instanceof DeleteTileLayer
                || deleteTileRange instanceof DeleteTileParametersId
                || deleteTileRange instanceof DeleteTileZoom
        ) {
            return S3ObjectPathsForPrefix;
        }

        if (deleteTileRange instanceof DeleteTileObject) {
            return SingleTile;
        }

        if (deleteTileRange instanceof DeleteTileZoomInBoundedBox) {
            return TileRangeWithBoundedBoxIfTileExist;
        }

        if (deleteTileRange instanceof DeleteTilePrefix) {
            return RetryPendingTask;
        }

        return DefaultStrategy;
    }

    public enum ObjectPathStrategy {
        NoDeletionsRequired,
        SingleTile,
        S3ObjectPathsForPrefix,
        S3ObjectPathsForPrefixFilterByBoundedBox,
        TileRangeWithBoundedBox,
        TileRangeWithBoundedBoxIfTileExist,
        RetryPendingTask,
        DefaultStrategy
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private AmazonS3Wrapper amazonS3Wrapper;
        private S3ObjectsWrapper s3ObjectsWrapper;
        private String bucketName;
        private DeleteTileRange deleteTileRange;
        private Integer batch;
        private Callback callback;

        public Builder withS3ObjectsWrapper(S3ObjectsWrapper s3ObjectsWrapper) {
            this.s3ObjectsWrapper = s3ObjectsWrapper;
            return this;
        }

        public Builder withBucket(String bucketName) {
            this.bucketName = bucketName;
            return this;
        }

        public Builder withDeleteRange(DeleteTileRange deleteTileRange) {
            this.deleteTileRange = deleteTileRange;
            return this;
        }

        public Builder withAmazonS3Wrapper(AmazonS3Wrapper amazonS3Wrapper) {
            this.amazonS3Wrapper = amazonS3Wrapper;
            return this;
        }

        public Builder withBatch(int batch) {
            this.batch = batch;
            return this;
        }

        public Builder withCallback(Callback callback) {
            this.callback = callback;
            return this;
        }

        // Ensure that the built task will be functional
        public BulkDeleteTask build() {
            checkNotNull(amazonS3Wrapper, "Missing AmazonS3Wrapper");
            checkNotNull(s3ObjectsWrapper, "Missing S3ObjectsWrapper");
            checkNotNull(bucketName, "Missing bucket");
            checkNotNull(deleteTileRange, "Missing DeleteRange");
            checkNotNull(batch, "Missing Batch");
            checkNotNull(callback, "Missing Callback");

            return new BulkDeleteTask(amazonS3Wrapper, s3ObjectsWrapper, bucketName, deleteTileRange, callback, batch);
        }
    }
}
