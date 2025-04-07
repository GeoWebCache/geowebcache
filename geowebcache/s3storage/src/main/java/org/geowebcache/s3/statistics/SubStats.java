package org.geowebcache.s3.statistics;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.geowebcache.s3.delete.BulkDeleteTask;
import org.geowebcache.s3.delete.DeleteTileRange;

public class SubStats {
    final BulkDeleteTask.ObjectPathStrategy strategy;
    final DeleteTileRange deleteTileRange;
    long deleted;
    long processed;
    long count = 1;
    long batchSent = 0;
    long batchTotal = 0;
    long batchLowTideLevel = 0;
    long batchHighTideLevel = 0;
    long bytes = 0;

    final List<Exception> recoverableIssues = new ArrayList<>();
    final List<Exception> nonRecoverableIssues = new ArrayList<>();
    final List<Exception> unknownIssues = new ArrayList<>();

    public SubStats(DeleteTileRange deleteTileRange, BulkDeleteTask.ObjectPathStrategy strategy) {
        checkNotNull(deleteTileRange, "deleteTileRange cannot be null");
        checkNotNull(strategy, "strategy cannot be null");

        this.deleteTileRange = deleteTileRange;
        this.strategy = strategy;
    }

    public boolean completed() {
        return recoverableIssues.isEmpty() && nonRecoverableIssues.isEmpty() && unknownIssues.isEmpty();
    }

    public void addBatch(BatchStats batchStats) {
        processed = getProcessed() + batchStats.getProcessed();
        deleted = getDeleted() + batchStats.getDeleted();
        batchSent = getBatchSent() + 1;
        batchTotal = getBatchTotal() + batchStats.getProcessed();
        batchLowTideLevel = getBatchLowTideLevel() == 0
                ? batchStats.getProcessed()
                : Math.min(batchStats.getProcessed(), getBatchLowTideLevel());
        batchHighTideLevel = Math.max(batchStats.getProcessed(), getBatchHighTideLevel());
        bytes += batchStats.getBytes();
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

    public Stream<Exception> getRecoverableIssues() {
        return recoverableIssues.stream();
    }

    public void addRecoverableIssue(Exception e) {
        this.recoverableIssues.add(e);
    }

    public int getRecoverableIssuesSize() {
        return recoverableIssues.size();
    }

    public void addNonRecoverableIssue(Exception e) {
        this.nonRecoverableIssues.add(e);
    }

    public Stream<Exception> getNonRecoverableIssues() {
        return nonRecoverableIssues.stream();
    }

    public int getNonRecoverableIssuesSize() {
        return nonRecoverableIssues.size();
    }

    public Stream<Exception> getUnknownIssues() {
        return unknownIssues.stream();
    }

    public int getUnknownIssuesSize() {
        return unknownIssues.size();
    }

    public void addUnknownIssue(Exception e) {
        this.unknownIssues.add(e);
    }

    public long getBytes() {
        return bytes;
    }


}
