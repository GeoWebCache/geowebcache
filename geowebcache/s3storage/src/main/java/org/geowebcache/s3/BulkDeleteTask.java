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
    public Long call() throws Exception {
        try {
            return deleteTileRange.stream()
                    .map(this::performDeleteStrategy)
                    .mapToLong(statistics::addSubStats)
                    .sum();

        } catch (Exception e) {
            S3BlobStore.log.severe(format("Exiting from bulk delete task: %s", e.getMessage()));
            statistics.nonrecoverableIssues.add(e);
            throw e;
            // return statistics.deleted;
        } finally {
            callback.results(statistics);
        }
    }

    private SubStats performDeleteStrategy(DeleteTileRange deleteRange) {
        SubStats stats;
        switch (chooseStrategy(deleteRange)) {
            case NoDeletionsRequired:
                stats = noDeletionsRequired(deleteRange);
                break;
            case SingleTile:
                stats = singleTile(deleteRange);
                break;
            case S3ObjectPathsForPrefix:
                stats = s3ObjectPathsForPrefix(deleteRange);
                break;
            case S3ObjectPathsForPrefixFilterByBoundedBox:
                stats = s3ObjectPathsForPrefixFilterByBoundedBox(deleteRange);
                break;
            case TileRangeWithBoundedBox:
                stats = tileRangeWithBounderBox(deleteRange);
                break;
            case TileRangeWithBoundedBoxIfTileExist:
                stats = tileRangeWithBounderBoxIfTileExists(deleteRange);
                break;
            default:
                stats = s3ObjectPathsForPrefix(deleteTileRange);
        }
        return stats;
    }

    private SubStats singleTile(DeleteTileRange deleteRange) {
        SubStats stats = new SubStats(deleteRange.path(), SingleTile);
        PerformDeleteObjects performDeleteObjects = new PerformDeleteObjects(amazonS3Wrapper, stats);
        var processDeletedObjects = new ProcessDeletedObjects(stats);

        S3BlobStore.log.info(format(
                "Using strategy SingleTile to a delete tile from bucket %s with prefix: %s",
                bucketName, deleteTileRange.path()));

        Long count = batchedStreamOfKeyObjects(deleteTileRange, stats)
                .map(mapKeyObjectsToDeleteObjectRequest)
                .map(performDeleteObjects)
                .mapToLong(processDeletedObjects)
                .sum();

        S3BlobStore.log.info(format(
                "Finished applying strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s deleted: %d",
                bucketName, deleteTileRange.path(), stats.deleted));

        return stats;
    }

    private SubStats tileRangeWithBounderBox(DeleteTileRange deleteTileRange) {
        S3BlobStore.log.warning("Strategy TileRangeWithBounderBox not implemented");
        return new SubStats(deleteTileRange.path(), TileRangeWithBoundedBox);
    }

    private SubStats tileRangeWithBounderBoxIfTileExists(DeleteTileRange deleteTileRange) {
        S3BlobStore.log.warning("Strategy TileRangeWithBounderBoxIfTileExists not implemented");
        return new SubStats(deleteTileRange.path(), TileRangeWithBoundedBoxIfTileExist);
    }

    private SubStats s3ObjectPathsForPrefixFilterByBoundedBox(DeleteTileRange deleteTileRange) {
        S3BlobStore.log.warning("Strategy S3ObjectPathsForPrefixFilterByBoundedBox not implemented");
        return new SubStats(deleteTileRange.path(), S3ObjectPathsForPrefixFilterByBoundedBox);
    }

    private SubStats noDeletionsRequired(DeleteTileRange deleteTileRange) {
        S3BlobStore.log.warning("Strategy NoDeletionsRequired nothing to do");
        return new SubStats(deleteTileRange.path(), NoDeletionsRequired);
    }

    private SubStats s3ObjectPathsForPrefix(DeleteTileRange deleteTileRange) {
        SubStats stats = new SubStats(deleteTileRange.path(), S3ObjectPathsForPrefix);
        var performDeleteObjects = new PerformDeleteObjects(amazonS3Wrapper, stats);
        var processDeletedObjects = new ProcessDeletedObjects(stats);

        S3BlobStore.log.info(format(
                "Using strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s",
                bucketName, deleteTileRange.path()));

        var count = batchedStreamOfKeyObjects(deleteTileRange, stats)
                .map(mapKeyObjectsToDeleteObjectRequest)
                .map(performDeleteObjects)
                .mapToLong(processDeletedObjects)
                .sum();

        if (count != stats.deleted) {
            S3BlobStore.log.warning(format("Mismatch during tile delete expected %d found %d", count, stats.deleted));
        }

        S3BlobStore.log.info(format(
                "Finished applying strategy S3ObjectPathsForPrefix to delete tiles of bucket %s with prefix: %s deleted: %d",
                bucketName, deleteTileRange.path(), count));

        return stats;
    }

    private Stream<List<KeyObject>> batchedStreamOfKeyObjects(DeleteTileRange deleteTileRange, SubStats stats) {
        return BatchingIterator.batchedStreamOf(
                generateStreamOfKeyObjects(createS3ObjectPathsForPrefixSupplier(deleteTileRange.path()), stats), batch);
    }

    private Stream<KeyObject> generateStreamOfKeyObjects(S3ObjectPathsForPrefixSupplier supplier, SubStats subStats) {
        return Stream.generate(supplier)
                .takeWhile(Objects::nonNull)
                .map(mapS3ObjectSummaryToKeyObject)
                .peek(key -> subStats.processed += 1);
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
        void results(Statistics statistics);
    }

    public static class LoggingCallback implements Callback {
        private static final Logger LOG = S3BlobStore.log;

        @Override
        public void results(Statistics statistics) {
            String message = format(
                    "Completed: %b Processed %s Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d Batches Sent %d Batches Total %d High Tide %d Low Tide %d",
                    statistics.completed(),
                    statistics.processed,
                    statistics.deleted,
                    statistics.recoverableIssues.size(),
                    statistics.unknownIssues.size(),
                    statistics.nonrecoverableIssues.size(),
                    statistics.batchSent,
                    statistics.batchTotal,
                    statistics.batchHighTideLevel,
                    statistics.batchLowTideLevel);
            if (statistics.completed()) {
                LOG.info(message);
            } else {
                LOG.warning(message);
            }

            for (var entry : statistics.statsPerStrategy.entrySet()) {
                var strategy = entry.getKey();
                var stats = entry.getValue();
                LOG.info(format(
                        "Strategy %s Count: %d Processed %d Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d Batches Sent %d Batches Total %d High Tide %d Low Tide %d",
                        strategy.toString(),
                        stats.count,
                        stats.processed,
                        stats.deleted,
                        stats.recoverableIssues.size(),
                        stats.unknownIssues.size(),
                        stats.nonrecoverableIssues.size(),
                        stats.batchSent,
                        stats.batchTotal,
                        stats.batchHighTideLevel,
                        stats.batchLowTideLevel));
            }

            for (var entry : statistics.statsPerPrefix.entrySet()) {
                var prefix = entry.getKey();
                var stats = entry.getValue();
                LOG.info(format(
                        "Prefix %s Count: %d Processed %d Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d Batches Sent %d Batches Total %d High Tide %d Low Tide %d",
                        prefix,
                        stats.count,
                        stats.processed,
                        stats.deleted,
                        stats.recoverableIssues.size(),
                        stats.unknownIssues.size(),
                        stats.nonrecoverableIssues.size(),
                        stats.batchSent,
                        stats.batchTotal,
                        stats.batchHighTideLevel,
                        stats.batchLowTideLevel));
            }
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
        final List<Exception> recoverableIssues = new ArrayList<>();
        final List<Exception> nonrecoverableIssues = new ArrayList<>();
        final List<Exception> unknownIssues = new ArrayList<>();

        final Map<String, SubStats> statsPerPrefix = new HashMap<>();
        final Map<ObjectPathStrategy, SubStats> statsPerStrategy = new HashMap<>();

        boolean completed() {
            return recoverableIssues.isEmpty() && nonrecoverableIssues.isEmpty() && unknownIssues.isEmpty();
        }

        public Map<String, SubStats> getStatsPerPrefix() {
            return statsPerPrefix;
        }

        boolean shouldRetry() {
            return !completed() && (!nonrecoverableIssues.isEmpty() || !unknownIssues.isEmpty());
        }

        Long addSubStats(SubStats stats) {
            String prefix = stats.prefix;
            ObjectPathStrategy strategy = stats.strategy;

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
            this.batchSent += stats.batchSent;
            this.batchTotal += stats.batchTotal;
            this.batchLowTideLevel = batchLowTideLevel == 0
                    ? stats.batchLowTideLevel
                    : Math.min(stats.batchLowTideLevel, batchLowTideLevel);
            this.batchHighTideLevel = Math.max(stats.batchHighTideLevel, batchHighTideLevel);

            return this.deleted;
        }

        public static class SubStats {
            String prefix;
            ObjectPathStrategy strategy;
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

            public SubStats(String prefix, ObjectPathStrategy strategy) {
                checkNotNull(prefix, "prefix cannot be null");
                checkNotNull(strategy, "strategy cannot be null");

                this.prefix = prefix;
                this.strategy = strategy;
            }

            public void merge(SubStats stats) {
                this.count += stats.count;
                this.deleted += stats.deleted;
                this.processed += stats.processed;
                this.recoverableIssues.addAll(stats.recoverableIssues);
                this.nonrecoverableIssues.addAll(stats.nonrecoverableIssues);
                this.unknownIssues.addAll(stats.unknownIssues);
            }

            public void incrementDeleted(long count) {
                deleted += count;
            }

            public void updateBatches(long size) {
                batchSent += 1;
                batchTotal += size;
                batchLowTideLevel = batchLowTideLevel == 0 ? size : Math.min(size, batchLowTideLevel);
                batchHighTideLevel = Math.max(size, batchHighTideLevel);
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
