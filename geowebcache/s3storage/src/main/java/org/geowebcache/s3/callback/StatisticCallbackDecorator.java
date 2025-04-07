package org.geowebcache.s3.callback;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import java.util.Objects;
import java.util.logging.Logger;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;

/**
 * This class has the responsibility of managing the statistics and logging of delete tasks as they are processed
 *
 * <p>When the taskEnded is called it will dump a summary of activity
 */
public class StatisticCallbackDecorator implements Callback {
    final Logger logger;

    final Callback delegate;
    Statistics statistics;
    SubStats currentSub;
    BatchStats currentBatch;

    public StatisticCallbackDecorator(Logger logger) {
        this(logger, new NoopCallback());
    }

    public StatisticCallbackDecorator(Logger logger, Callback delegate) {
        checkNotNull(delegate, "delegate parameter cannot be null");
        checkNotNull(logger, "logger parameter cannot be null");

        this.logger = logger;
        this.delegate = delegate;
    }

    @Override
    public void taskEnded() {
        checkState(Objects.nonNull(statistics), "Statistics not initialized");

        try {
            String message = format(
                    "Completed: %b Processed %s Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d Batches Sent %d Batches Total %d High Tide %d Low Tide %d Bytes Deleted: %d",
                    statistics.completed(),
                    statistics.getProcessed(),
                    statistics.getDeleted(),
                    statistics.getNonRecoverableIssuesSize(),
                    statistics.getRecoverableIssuesSize(),
                    statistics.getUnknownIssuesSize(),
                    statistics.getBatchSent(),
                    statistics.getBatchTotal(),
                    statistics.getBatchHighTideLevel(),
                    statistics.getBatchLowTideLevel(),
                    statistics.getBytes()
            );
            if (statistics.completed()) {
                logger.info(message);
            } else {
                logger.warning(message);
            }

            for (var subStat : statistics.getSubStats()) {
                logger.info(format(
                        "Strategy %s Count: %d Processed %d Deleted: %d Recoverable Errors: %d Unrecoverable Errors: %d Unknown Issues %d Batches Sent %d Batches Total %d High Tide %d Low Tide %d Bytes Deleted %d",
                        subStat.getStrategy().toString(),
                        subStat.getCount(),
                        subStat.getProcessed(),
                        subStat.getDeleted(),
                        subStat.getRecoverableIssuesSize(),
                        subStat.getUnknownIssuesSize(),
                        subStat.getNonRecoverableIssuesSize(),
                        subStat.getBatchSent(),
                        subStat.getBatchTotal(),
                        subStat.getBatchHighTideLevel(),
                        subStat.getBatchLowTideLevel(),
                        subStat.getBytes()
                ));
            }
        } finally {
            delegate.taskEnded();
        }
    }

    @Override
    public void tileResult(ResultStat result) {
        checkNotNull(result, "result parameter cannot be null");
        checkState(Objects.nonNull(currentBatch), "current batch field cannot be null");

        currentBatch.add(result);
        delegate.tileResult(result);
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
        checkNotNull(subStats, "subStats parameter cannot be null");
        checkState(Objects.nonNull(statistics), "task should have been been started");
        checkState(Objects.isNull(currentSub), "Sub task has already been started");
        this.currentSub = subStats;
        delegate.subTaskStarted(subStats);
    }

    @Override
    public void subTaskEnded() {
        checkState(Objects.nonNull(this.statistics), "statistics field should have been set");
        checkState(Objects.nonNull(currentSub), "no current sub stats have been set");
        this.statistics.addSubStats(currentSub);
        currentSub = null;
        delegate.subTaskEnded();
    }

    @Override
    public void taskStarted(Statistics statistics) {
        checkNotNull(statistics, "statistics parameter cannot be null");
        checkState(Objects.isNull(this.statistics), "statistics field should have been set");

        this.statistics = statistics;
        delegate.taskStarted(statistics);
    }
}
