package org.geowebcache.s3.callback;

import org.geowebcache.s3.delete.BulkDeleteTask;
import org.geowebcache.s3.delete.BulkDeleteTask.Callback;
import org.geowebcache.s3.delete.BulkDeleteTask.NoopCallback;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class CaptureCallback implements Callback {
    private final Callback delegate;

    long batchStartedCount = 0;
    long batchEndedCount = 0;
    long subTaskStartedCount = 0;
    long subTaskEndedCount = 0;
    long taskStartedCount = 0;
    long taskEndedCount = 0;
    long tileDeletedCount = 0;

    Statistics statistics = null;
    List<SubStats> subStats = new ArrayList<>();
    List<BatchStats> batchStats = new ArrayList<>();

    public Callback getDelegate() {
        return delegate;
    }

    public long getBatchStartedCount() {
        return batchStartedCount;
    }

    public long getBatchEndedCount() {
        return batchEndedCount;
    }

    public long getSubTaskStartedCount() {
        return subTaskStartedCount;
    }

    public long getSubTaskEndedCount() {
        return subTaskEndedCount;
    }

    public long getTaskStartedCount() {
        return taskStartedCount;
    }

    public long getTaskEndedCount() {
        return taskEndedCount;
    }

    public long getTileDeletedCount() {
        return tileDeletedCount;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public List<SubStats> getSubStats() {
        return subStats;
    }

    public List<BatchStats> getBatchStats() {
        return batchStats;
    }

    public CaptureCallback() {
        this(new NoopCallback());
    }

    public CaptureCallback(Callback delegate) {
        this.delegate = delegate;
    }

    @Override
    public void tileDeleted(ResultStat result) {
        this.delegate.tileDeleted(result);
        tileDeletedCount++;
    }

    @Override
    public void batchStarted(BatchStats batchStats) {
        this.delegate.batchStarted(batchStats);
        this.batchStats.add(batchStats);
        batchStartedCount++;
    }

    @Override
    public void batchEnded() {
        this.delegate.batchEnded();
        batchEndedCount++;
    }

    @Override
    public void subTaskStarted(SubStats subStats) {
        this.delegate.subTaskStarted(subStats);
        this.subStats.add(subStats);
        subTaskStartedCount++;
    }

    @Override
    public void subTaskEnded() {
        this.delegate.subTaskEnded();
        subTaskEndedCount++;
    }

    @Override
    public void taskStarted(Statistics statistics) {
        this.delegate.taskStarted(statistics);
        this.statistics = statistics;
        taskStartedCount++;
    }

    @Override
    public void taskEnded() {
        this.delegate.taskEnded();
        taskEndedCount++;
    }
}
