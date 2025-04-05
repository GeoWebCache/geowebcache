package org.geowebcache.s3.statistics;

import org.geowebcache.s3.delete.DeleteTileRange;
import org.geowebcache.s3.delete.BulkDeleteTask;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class SubStats {
    private final BulkDeleteTask.ObjectPathStrategy strategy;
    private final DeleteTileRange deleteTileRange;
    private long deleted;
    private long processed;
    private long count = 1;
    private long batchSent = 0;
    private long batchTotal = 0;
    private long batchLowTideLevel = 0;
    private long batchHighTideLevel = 0;

    private final List<Exception> recoverableIssues = new ArrayList<>();
    private final List<Exception> nonrecoverableIssues = new ArrayList<>();
    private final List<Exception> unknownIssues = new ArrayList<>();

    public SubStats(DeleteTileRange deleteTileRange, BulkDeleteTask.ObjectPathStrategy strategy) {
        checkNotNull(deleteTileRange, "deleteTileRange cannot be null");
        checkNotNull(strategy, "strategy cannot be null");

        this.deleteTileRange = deleteTileRange;
        this.strategy = strategy;
    }

    public boolean completed() {
        return getRecoverableIssues().isEmpty() && getNonrecoverableIssues().isEmpty() && getUnknownIssues().isEmpty();
    }

    public void addBatch(BatchStats batchStats) {
        processed = getProcessed() + batchStats.getProcessed();
        deleted = getDeleted() + batchStats.getDeleted();
        batchSent = getBatchSent() + 1;
        batchTotal = getBatchTotal() + batchStats.getProcessed();
        batchLowTideLevel = getBatchLowTideLevel() == 0 ? batchStats.getProcessed() : Math.min(batchStats.getProcessed(), getBatchLowTideLevel());
        batchHighTideLevel = Math.max(batchStats.getProcessed(), getBatchHighTideLevel());
    }

    public BulkDeleteTask.ObjectPathStrategy getStrategy() {
        return strategy;
    }

    public DeleteTileRange getDeleteTileRange() {
        return deleteTileRange;
    }

    public long getDeleted() {
        return deleted;
    }

    public long getProcessed() {
        return processed;
    }

    public long getCount() {
        return count;
    }

    public long getBatchSent() {
        return batchSent;
    }

    public long getBatchTotal() {
        return batchTotal;
    }

    public long getBatchLowTideLevel() {
        return batchLowTideLevel;
    }

    public long getBatchHighTideLevel() {
        return batchHighTideLevel;
    }

    public List<Exception> getRecoverableIssues() {
        return recoverableIssues;
    }

    public List<Exception> getNonrecoverableIssues() {
        return nonrecoverableIssues;
    }

    public List<Exception> getUnknownIssues() {
        return unknownIssues;
    }
}
