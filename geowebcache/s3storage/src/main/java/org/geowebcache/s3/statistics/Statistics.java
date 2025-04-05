package org.geowebcache.s3.statistics;

import org.geowebcache.s3.delete.DeleteTileRange;

import java.util.ArrayList;
import java.util.List;


public class Statistics {
    private long deleted;
    private long processed;
    private long batchSent = 0;
    private long batchTotal = 0;
    private long batchLowTideLevel = 0;
    private long batchHighTideLevel = 0;
    private final DeleteTileRange deleteTileRange;
    private final List<Exception> recoverableIssues = new ArrayList<>();
    private final List<Exception> nonrecoverableIssues = new ArrayList<>();
    private final List<Exception> unknownIssues = new ArrayList<>();

    private final List<SubStats> subStats = new ArrayList<>();

    public Statistics(DeleteTileRange deleteTileRange) {
        this.deleteTileRange = deleteTileRange;
    }

    public boolean completed() {
        return getRecoverableIssues().isEmpty() && getNonrecoverableIssues().isEmpty() && getUnknownIssues().isEmpty();
    }

    public List<SubStats> getSubStats() {
        return subStats;
    }

    public void addSubStats(SubStats stats) {
        this.getSubStats().add(stats);
        this.deleted = this.getDeleted() + stats.getDeleted();
        this.processed = this.getProcessed() + stats.getProcessed();
        this.getRecoverableIssues().addAll(stats.getRecoverableIssues());
        this.getNonrecoverableIssues().addAll(stats.getNonrecoverableIssues());
        this.getUnknownIssues().addAll(stats.getUnknownIssues());
        this.batchSent = this.getBatchSent() + stats.getBatchSent();
        this.batchTotal = this.getBatchTotal() + stats.getBatchTotal();
        this.batchLowTideLevel = getBatchLowTideLevel() == 0
                ? stats.getBatchLowTideLevel()
                : Math.min(stats.getBatchLowTideLevel(), getBatchLowTideLevel());
        this.batchHighTideLevel = Math.max(stats.getBatchHighTideLevel(), getBatchHighTideLevel());
    }

    public long getDeleted() {
        return deleted;
    }

    public long getProcessed() {
        return processed;
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

    public DeleteTileRange getDeleteTileRange() {
        return deleteTileRange;
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

