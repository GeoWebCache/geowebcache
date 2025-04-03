package org.geowebcache.s3;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.geowebcache.s3.BulkDeleteTask.ObjectPathStrategy.*;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.geowebcache.s3.BulkDeleteTask.Statistics.SubStats;
import org.geowebcache.util.KeyObject;

class BulkDeleteTask implements Callable<Long> {
    private final AmazonS3Wrapper amazonS3Wrapper;
    private final S3ObjectsWrapper s3ObjectsWrapper;
    private final String bucketName;
    private final DeleteTileRange deleteTileRange;
    private final int batch;
    private final Callback callback;
    private final Statistics statistics = new Statistics();

    private final ThreadNotInterruptedPredicate threadNotInterrupted = new ThreadNotInterruptedPredicate();
    private final MapS3ObjectSummaryToKeyObject mapS3ObjectSummaryToKeyObject = new MapS3ObjectSummaryToKeyObject();
    private final MapKeyObjectsToDeleteObjectRequest mapKeyObjectsToDeleteObjectRequest;

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
        this.mapKeyObjectsToDeleteObjectRequest = new MapKeyObjectsToDeleteObjectRequest(bucketName);
        this.batch = batch;
        this.callback = callback;
    }

    @Override
    public Long call() throws Exception {
        switch (chooseStrategy()) {
            case NoDeletionsRequired:
                break;
            case S3ObjectPathsForPrefix:
                statistics.deleted = s3ObjectPathsForPrefix(deleteTileRange);
                break;
            case S3ObjectPathsForPrefixFilterByBoundedBox:
                statistics.deleted = s3ObjectPathsForPrefixFilterByBoundedBox(deleteTileRange);
                break;
            case TileRangeWithBoundedBox:
                statistics.deleted = tileRangeWithBounderBox(deleteTileRange);
                break;
            case TileRangeWithBoundedBoxIfTileExist:
                statistics.deleted = tileRangeWithBounderBoxIfTileExists(deleteTileRange);
                break;
            case DefaultStrategy:
                statistics.deleted = s3ObjectPathsForPrefix(deleteTileRange);
        }

        // Inform call of activity
        callback.results(statistics);

        return statistics.deleted;
    }

    private long tileRangeWithBounderBox(DeleteTileRange deleteTileRange) {
        statistics.addSubStats(deleteTileRange.prefix(), TileRangeWithBoundedBox, new SubStats());
        S3BlobStore.log.warning("Strategy TileRangeWithBounderBox not implemented");
        return 0;
    }

    private long tileRangeWithBounderBoxIfTileExists(DeleteTileRange deleteTileRange) {
        statistics.addSubStats(deleteTileRange.prefix(), TileRangeWithBoundedBoxIfTileExist, new SubStats());
        S3BlobStore.log.warning("Strategy TileRangeWithBounderBoxIfTileExists not implemented");
        return 0;
    }

    private long s3ObjectPathsForPrefixFilterByBoundedBox(DeleteTileRange deleteTileRange) {
        statistics.addSubStats(deleteTileRange.prefix(), S3ObjectPathsForPrefixFilterByBoundedBox, new SubStats());
        S3BlobStore.log.warning("Strategy S3ObjectPathsForPrefixFilterByBoundedBox not implemented");
        return 0;
    }

    private long NoDeletionsRequired(DeleteTileRange deleteTileRange) {
        statistics.addSubStats(deleteTileRange.prefix(), NoDeletionsRequired, new SubStats());
        S3BlobStore.log.warning("Strategy NoDeletionsRequired nothing to do");
        return 0;
    }

    private long s3ObjectPathsForPrefix(DeleteTileRange deleteTileRange) {
        statistics.addSubStats(deleteTileRange.prefix(), S3ObjectPathsForPrefix, new SubStats());
        var performDeleteObjects = new PerformDeleteObjects(amazonS3Wrapper);

        S3BlobStore.log.info(format(
                "Using strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s",
                bucketName, deleteTileRange.prefix()));

        var count = BatchingIterator.batchedStreamOf(
                        generateStreamOfKeyObjects(createS3ObjectPathsForPrefixSupplier(deleteTileRange.prefix())),
                        batch)
                .map(mapKeyObjectsToDeleteObjectRequest)
                .map(performDeleteObjects)
                .mapToLong(r -> (long) r.getDeletedObjects().size())
                .sum();

        performDeleteObjects.getIssues().forEach(issue -> {
            switch (issue.getErrorType()) {
                case Client:
                    statistics.nonrecoverableIssues.add(issue);
                    break;
                case Service:
                    statistics.recoverableIssues.add(issue);
                    break;
                case Unknown:
                    statistics.unknownIssues.add(issue);
                    break;
            }
        });

        S3BlobStore.log.info(format(
                "Finished applying strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s deleted: %d",
                bucketName, deleteTileRange.prefix(), count));

        return count;
    }

    private Stream<KeyObject> generateStreamOfKeyObjects(S3ObjectPathsForPrefixSupplier supplier) {
        return Stream.generate(supplier).takeWhile(Objects::nonNull).map(mapS3ObjectSummaryToKeyObject);
    }

    private S3ObjectPathsForPrefixSupplier createS3ObjectPathsForPrefixSupplier(String prefix) {
        return S3ObjectPathsForPrefixSupplier.newBuilder()
                .withBucket(bucketName)
                .withPrefix(prefix)
                .withWrapper(s3ObjectsWrapper)
                .build();
    }

    ObjectPathStrategy chooseStrategy() {
        return DefaultStrategy;
    }

    public enum ObjectPathStrategy {
        NoDeletionsRequired,
        S3ObjectPathsForPrefix,
        S3ObjectPathsForPrefixFilterByBoundedBox,
        TileRangeWithBoundedBox,
        TileRangeWithBoundedBoxIfTileExist,
        DefaultStrategy
    }

    public interface Callback {
        void results(Statistics statistics);
    }

    public static class LoggingCallback implements Callback {
        private static final Logger LOG = S3BlobStore.log;

        @Override
        public void results(Statistics statistics) {
            LOG.info(format(
                    "Successful: %b Processed %s Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d",
                    statistics.completed(),
                    statistics.processed,
                    statistics.deleted,
                    statistics.recoverableIssues.size(),
                    statistics.unknownIssues.size(),
                    statistics.nonrecoverableIssues.size()));

            for (var entry : statistics.statsPerStrategy.entrySet()) {
                var strategy = entry.getKey();
                var stats = entry.getValue();
                LOG.info(format(
                        "Strategy %s Count: %d Processed %d Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d",
                        strategy.toString(),
                        stats.count,
                        stats.processed,
                        stats.deleted,
                        stats.recoverableIssues.size(),
                        stats.unknownIssues.size(),
                        stats.nonrecoverableIssues.size()));
            }

            for (var entry : statistics.statsPerPrefix.entrySet()) {
                var prefix = entry.getKey();
                var stats = entry.getValue();
                LOG.info(format(
                        "Prefix %s Count: %d Processed %d Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d",
                        prefix,
                        stats.count,
                        stats.processed,
                        stats.deleted,
                        stats.recoverableIssues.size(),
                        stats.unknownIssues.size(),
                        stats.nonrecoverableIssues.size()));
            }
        }
    }

    static Builder newBuilder() {
        return new Builder();
    }

    public static class Statistics {
        long deleted;
        long processed;
        final List<Exception> recoverableIssues = new ArrayList<>();
        final List<Exception> nonrecoverableIssues = new ArrayList<>();
        final List<Exception> unknownIssues = new ArrayList<>();
        final Map<String, SubStats> statsPerPrefix = new HashMap<>();
        final Map<ObjectPathStrategy, SubStats> statsPerStrategy = new HashMap<>();

        boolean completed() {
            return recoverableIssues.isEmpty() && nonrecoverableIssues.isEmpty() && unknownIssues.isEmpty();
        }

        boolean shouldRetry() {
            return !completed() && (!nonrecoverableIssues.isEmpty() || !unknownIssues.isEmpty());
        }

        void addSubStats(String prefix, ObjectPathStrategy strategy, SubStats stats) {
            if (statsPerPrefix.containsKey(prefix)) {
                var old = statsPerPrefix.get(prefix);
                old.merge(stats);
            } else {
                statsPerPrefix.put(prefix, stats);
            }

            if (statsPerStrategy.containsKey(strategy)) {
                var old = statsPerStrategy.get(strategy);
                old.merge(stats);
            } else {
                statsPerStrategy.put(strategy, stats);
            }

            this.deleted += stats.deleted;
            this.processed += stats.processed;
            this.recoverableIssues.addAll(stats.recoverableIssues);
            this.nonrecoverableIssues.addAll(stats.nonrecoverableIssues);
            this.unknownIssues.addAll(stats.unknownIssues);
        }

        public static class SubStats {
            long deleted;
            long processed;
            long count = 1;
            final List<Exception> recoverableIssues = new ArrayList<>();
            final List<Exception> nonrecoverableIssues = new ArrayList<>();
            final List<Exception> unknownIssues = new ArrayList<>();

            public void merge(SubStats stats) {
                this.count += stats.count;
                this.deleted += stats.deleted;
                this.processed += stats.processed;
                this.recoverableIssues.addAll(stats.recoverableIssues);
                this.nonrecoverableIssues.addAll(stats.nonrecoverableIssues);
                this.unknownIssues.addAll(stats.unknownIssues);
            }
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

        public Builder withLoggingCallback() {
            this.callback = new LoggingCallback();
            return this;
        }
    }
}
