package org.geowebcache.s3;

import org.geowebcache.s3.BulkDeleteTask.BatchStats;
import org.geowebcache.s3.BulkDeleteTask.ResultStat;
import org.geowebcache.s3.BulkDeleteTask.Statistics;
import org.geowebcache.s3.BulkDeleteTask.Statistics.SubStats;

import java.util.Objects;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

public class LoggingCallbackDecorator implements BulkDeleteTask.Callback {
    private static final Logger LOG = S3BlobStore.log;

    private final BulkDeleteTask.Callback delegate;
    private Statistics statistics;
    private SubStats currentSub;
    private BatchStats currentBatch;

    public LoggingCallbackDecorator() {
        this(new BulkDeleteTask.NoopCallback());
    }

    public LoggingCallbackDecorator(BulkDeleteTask.Callback delegate) {
        checkNotNull(delegate, "delegate parameter cannot be null");

        this.delegate = delegate;
    }

    @Override
    public void taskEnded() {
        try {
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

            for (var subStat : statistics.subStats) {
                LOG.info(format(
                        "Strategy %s Count: %d Processed %d Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d Batches Sent %d Batches Total %d High Tide %d Low Tide %d",
                        subStat.strategy.toString(),
                        subStat.count,
                        subStat.processed,
                        subStat.deleted,
                        subStat.recoverableIssues.size(),
                        subStat.unknownIssues.size(),
                        subStat.nonrecoverableIssues.size(),
                        subStat.batchSent,
                        subStat.batchTotal,
                        subStat.batchHighTideLevel,
                        subStat.batchLowTideLevel));
            }
        } finally {
            delegate.taskEnded();
        }
    }

    @Override
    public void tileDeleted(ResultStat result){
        checkNotNull(result, "result parameter cannot be null");
        checkState(Objects.nonNull(currentBatch), "current batch field cannot be null");

        currentBatch.add(result);
        delegate.tileDeleted(result);
    }


    @Override
    public void batchStarted(BatchStats statistics) {
        checkState(Objects.isNull(currentBatch), "Batch has already been started");
        this.currentBatch = statistics;
        delegate.batchStarted(statistics);
    }

    @Override
    public void batchEnded() {
        checkState(Objects.nonNull(currentBatch), "Batch has not been set, missing call to batchStarted");
        checkState(Objects.nonNull(currentSub), "SubStat has not been set, missing call to subTaskStarted");
        currentSub.addBatch(currentBatch);
        currentBatch = null;
        delegate.batchEnded();
    }

    @Override
    public void subTaskStarted(SubStats subStats) {
        checkState(Objects.isNull(currentSub), "Sub task has already been started");
        this.currentSub = subStats;
        delegate.subTaskStarted(subStats);
    }

    @Override
    public void subTaskEnded() {
        checkState(Objects.nonNull(this.statistics), "statistics fields should have been set");
        checkState(Objects.nonNull(currentSub), "no current sub stats have been set");
        this.statistics.addSubStats(currentSub);
        currentSub = null;
        delegate.subTaskEnded();
    }

    @Override
    public void taskStarted(Statistics statistics) {
        this.statistics = statistics;
        delegate.taskStarted(statistics);
    }
}

