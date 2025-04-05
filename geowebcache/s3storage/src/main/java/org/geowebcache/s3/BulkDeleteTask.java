package org.geowebcache.s3;

import org.geowebcache.s3.BulkDeleteTask.Statistics.SubStats;
import org.geowebcache.storage.TileObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.geowebcache.s3.BulkDeleteTask.ObjectPathStrategy.*;

class BulkDeleteTask implements Callable<Long> {
    private final AmazonS3Wrapper amazonS3Wrapper;
    private final S3ObjectsWrapper s3ObjectsWrapper;
    private final String bucketName;
    private final DeleteTileRange deleteTileRange;
    private final int batch;

    private final Callback callback;

    //private final ThreadNotInterruptedPredicate threadNotInterrupted = new ThreadNotInterruptedPredicate();
    private final MapS3ObjectSummaryToKeyObject mapS3ObjectSummaryToKeyObject = new MapS3ObjectSummaryToKeyObject();
    private final MapKeyObjectsToDeleteObjectRequest mapKeyObjectsToDeleteObjectRequest = new MapKeyObjectsToDeleteObjectRequest();

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
            S3BlobStore.log.severe(format("Exiting from bulk delete task: %s", e.getMessage()));
            statistics.nonrecoverableIssues.add(e);
            return statistics.processed;
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
                return tileRangeWithBounderBox(deleteRange);
            case TileRangeWithBoundedBoxIfTileExist:
                return tileRangeWithBounderBoxIfTileExists(deleteRange);
            default:
                return s3ObjectPathsForPrefix(deleteTileRange);
        }
    }

    private Long singleTile(DeleteTileRange deleteRange) {
        SubStats subStats = new SubStats(deleteTileRange, SingleTile);
        callback.subTaskStarted(subStats);

        PerformDeleteObjects performDeleteObjects = new PerformDeleteObjects(amazonS3Wrapper, bucketName, callback, subStats, deleteTileRange);

        S3BlobStore.log.info(format(
                "Using strategy SingleTile to a delete tile from bucket %s with prefix: %s",
                bucketName, deleteTileRange.path()));

        Long count = batchedStreamOfKeyObjects(deleteTileRange, subStats)
                .map(mapKeyObjectsToDeleteObjectRequest)
                .mapToLong(performDeleteObjects)
                .sum();

        S3BlobStore.log.info(format(
                "Finished applying strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s processed: %d",
                bucketName, deleteTileRange.path(), count));

        callback.subTaskEnded();
        return subStats.processed;
    }

    private Long tileRangeWithBounderBox(DeleteTileRange deleteTileRange) {
        S3BlobStore.log.warning("Strategy TileRangeWithBounderBox not implemented");
        SubStats subStats = new SubStats(deleteTileRange, TileRangeWithBoundedBox);
        callback.subTaskStarted(subStats);
        callback.subTaskEnded();
        return subStats.processed;
    }

    private Long tileRangeWithBounderBoxIfTileExists(DeleteTileRange deleteTileRange) {
        S3BlobStore.log.warning("Strategy TileRangeWithBounderBoxIfTileExists not implemented");
        SubStats subStats = new SubStats(deleteTileRange, TileRangeWithBoundedBoxIfTileExist);
        callback.subTaskStarted(subStats);
        callback.subTaskEnded();
        return subStats.processed;
    }

    private Long s3ObjectPathsForPrefixFilterByBoundedBox(DeleteTileRange deleteTileRange) {
        S3BlobStore.log.warning("Strategy S3ObjectPathsForPrefixFilterByBoundedBox not implemented");
        SubStats subStats = new SubStats(deleteTileRange, S3ObjectPathsForPrefixFilterByBoundedBox);
        callback.subTaskStarted(subStats);
        callback.subTaskEnded();
        return subStats.processed;
    }

    private Long noDeletionsRequired(DeleteTileRange deleteTileRange) {
        S3BlobStore.log.warning("Strategy NoDeletionsRequired nothing to do");
        SubStats subStats = new SubStats(deleteTileRange, NoDeletionsRequired);
        callback.subTaskStarted(subStats);
        callback.subTaskEnded();
        return subStats.processed;
    }

    private Long s3ObjectPathsForPrefix(DeleteTileRange deleteTileRange) {
        SubStats subStats = new SubStats(deleteTileRange, S3ObjectPathsForPrefix);
        callback.subTaskStarted(subStats);

        var performDeleteObjects = new PerformDeleteObjects(amazonS3Wrapper, bucketName, callback, subStats, deleteTileRange);

        S3BlobStore.log.info(format(
                "Using strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s",
                bucketName, deleteTileRange.path()));

        var count = batchedStreamOfKeyObjects(deleteTileRange, subStats)
                .map(mapKeyObjectsToDeleteObjectRequest)
                .mapToLong(performDeleteObjects)
                .sum();

        if (count != subStats.deleted) {
            S3BlobStore.log.warning(format("Mismatch during tile delete expected %d found %d", count, subStats.deleted));
        }

        S3BlobStore.log.info(format(
                "Finished applying strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s deleted: %d",
                bucketName, deleteTileRange.path(), count));

        callback.subTaskEnded();
        return subStats.processed;
    }

    private Stream<List<DeleteTileInfo>> batchedStreamOfKeyObjects(DeleteTileRange deleteTileRange, SubStats stats) {
        return BatchingIterator.batchedStreamOf(
                generateStreamOfKeyObjects(createS3ObjectPathsForPrefixSupplier(deleteTileRange.path()), stats), batch);
    }

    private Stream<DeleteTileInfo> generateStreamOfKeyObjects(S3ObjectPathsForPrefixSupplier supplier, SubStats subStats) {
        return Stream.generate(supplier)
                .takeWhile(Objects::nonNull)
                .map(mapS3ObjectSummaryToKeyObject);
    }

    private S3ObjectPathsForPrefixSupplier createS3ObjectPathsForPrefixSupplier(String prefix) {
        return S3ObjectPathsForPrefixSupplier.newBuilder()
                .withBucket(bucketName)
                .withPrefix(prefix)
                .withWrapper(s3ObjectsWrapper)
                .build();
    }

    ObjectPathStrategy chooseStrategy(DeleteTileRange deleteTileRange) {
        if (deleteTileRange instanceof DeleteTileLayer) {
            return S3ObjectPathsForPrefix;
        }

        if (deleteTileRange instanceof DeleteTileObject) {
            return SingleTile;
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
        DefaultStrategy
    }

    public interface Callback {
        void tileDeleted(ResultStat result);
        void batchStarted(BatchStats batchStats);
        void batchEnded();
        void subTaskStarted(SubStats subStats);
        void subTaskEnded();
        void taskStarted(Statistics statistics);
        void taskEnded();
    }

    public static class NoopCallback implements Callback {
        @Override
        public void tileDeleted(ResultStat result) {
        }
        @Override
        public void batchStarted(BatchStats batchStats) {
        }
        @Override
        public void batchEnded() {
        }
        @Override
        public void subTaskStarted(SubStats subStats) {
        }
        @Override
        public void subTaskEnded() {
        }
        @Override
        public void taskStarted(Statistics statistics) {
        }
        @Override
        public void taskEnded() {
        }
    }


    static Builder newBuilder() {
        return new Builder();
    }

    public static class Statistics {
        long deleted;
        long processed;
        long batchSent = 0;
        long batchTotal = 0;
        long batchLowTideLevel = 0;
        long batchHighTideLevel = 0;
        final DeleteTileRange deleteTileRange;
        final List<Exception> recoverableIssues = new ArrayList<>();
        final List<Exception> nonrecoverableIssues = new ArrayList<>();
        final List<Exception> unknownIssues = new ArrayList<>();

        final List<SubStats> subStats = new ArrayList<>();

        public Statistics(DeleteTileRange deleteTileRange) {
            this.deleteTileRange = deleteTileRange;
        }

        boolean completed() {
            return recoverableIssues.isEmpty() && nonrecoverableIssues.isEmpty() && unknownIssues.isEmpty();
        }

        public List<SubStats> getSubStats() {
            return subStats;
        }

        Long addSubStats(SubStats stats) {
            this.subStats.add(stats);
            this.deleted += stats.deleted;
            this.processed += stats.processed;
            this.recoverableIssues.addAll(stats.recoverableIssues);
            this.nonrecoverableIssues.addAll(stats.nonrecoverableIssues);
            this.unknownIssues.addAll(stats.unknownIssues);
            this.batchSent += stats.batchSent;
            this.batchTotal += stats.batchTotal;
            this.batchLowTideLevel = batchLowTideLevel == 0
                    ? stats.batchLowTideLevel
                    : Math.min(stats.batchLowTideLevel, batchLowTideLevel);
            this.batchHighTideLevel = Math.max(stats.batchHighTideLevel, batchHighTideLevel);

            return this.deleted;
        }

        public static class SubStats {
            ObjectPathStrategy strategy;
            DeleteTileRange deleteTileRange;
            long deleted;
            long processed;
            long count = 1;
            long batchSent = 0;
            long batchTotal = 0;
            long batchLowTideLevel = 0;
            long batchHighTideLevel = 0;

            final List<Exception> recoverableIssues = new ArrayList<>();
            final List<Exception> nonrecoverableIssues = new ArrayList<>();
            final List<Exception> unknownIssues = new ArrayList<>();

            public SubStats(DeleteTileRange deleteTileRange, ObjectPathStrategy strategy) {
                checkNotNull(deleteTileRange, "deleteTileRange cannot be null");
                checkNotNull(strategy, "strategy cannot be null");

                this.deleteTileRange = deleteTileRange;
                this.strategy = strategy;
            }

            boolean completed() {
                return recoverableIssues.isEmpty() && nonrecoverableIssues.isEmpty() && unknownIssues.isEmpty();
            }

            public void addBatch(BatchStats batchStats) {
                processed += batchStats.processed;
                deleted += batchStats.deleted;
                batchSent += 1;
                batchTotal += batchStats.processed;
                batchLowTideLevel = batchLowTideLevel == 0 ? batchStats.processed : Math.min(batchStats.processed, batchLowTideLevel);
                batchHighTideLevel = Math.max(batchStats.processed, batchHighTideLevel);
            }
        }
    }

    public static class BatchStats {
        DeleteTileRange deleteTileRange;
        long deleted;
        long processed;


        BatchStats(DeleteTileRange deleteTileRange) {
            checkNotNull(deleteTileRange, "deleteTileRange cannot be null");
            this.deleteTileRange = deleteTileRange;
        }

        public void setProcessed (long processed) {
            this.processed = processed;
        }

        public void add(ResultStat stat) {
            deleted += 1;
        }
    }

    public static class ResultStat {
        String path;
        TileObject tileObject;  // Can be null?
        long size;
        long when;

        public ResultStat(String path, TileObject tileObject, long size, long when) {
            this.path = path;
            this.tileObject = tileObject;
            this.size = size;
            this.when = when;
        }
    }

    static class Builder {
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
