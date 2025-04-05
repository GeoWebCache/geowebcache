package org.geowebcache.s3.callback;

import org.geowebcache.s3.S3BlobStore;
import org.geowebcache.s3.delete.BulkDeleteTask;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;

import java.util.Objects;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

public class LoggingCallbackDecorator implements BulkDeleteTask.Callback {
    private static final Logger LOG = S3BlobStore.getLog();

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
                    statistics.getProcessed(),
                    statistics.getDeleted(),
                    statistics.getRecoverableIssues().size(),
                    statistics.getRecoverableIssues().size(),
                    statistics.getUnknownIssues().size(),
                    statistics.getBatchSent(),
                    statistics.getBatchTotal(),
                    statistics.getBatchHighTideLevel(),
                    statistics.getBatchLowTideLevel());
            if (statistics.completed()) {
                LOG.info(message);
            } else {
                LOG.warning(message);
            }

            for (var subStat : statistics.getSubStats()) {
                LOG.info(format(
                        "Strategy %s Count: %d Processed %d Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d Batches Sent %d Batches Total %d High Tide %d Low Tide %d",
                        subStat.getStrategy().toString(),
                        subStat.getCount(),
                        subStat.getProcessed(),
                        subStat.getDeleted(),
                        subStat.getRecoverableIssues().size(),
                        subStat.getUnknownIssues().size(),
                        subStat.getNonrecoverableIssues().size(),
                        subStat.getBatchSent(),
                        subStat.getBatchTotal(),
                        subStat.getBatchHighTideLevel(),
                        subStat.getBatchLowTideLevel()));
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

