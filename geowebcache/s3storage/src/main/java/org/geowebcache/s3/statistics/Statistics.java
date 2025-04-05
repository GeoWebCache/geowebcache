package org.geowebcache.s3.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.geowebcache.s3.delete.DeleteTileRange;

public class Statistics {
    long deleted;
    long processed;
    long batchSent = 0;
    long batchTotal = 0;
    long batchLowTideLevel = 0;
    long batchHighTideLevel = 0;
    final DeleteTileRange deleteTileRange;
    final List<Exception> recoverableIssues = new ArrayList<>();
    final List<Exception> nonRecoverableIssues = new ArrayList<>();
    final List<Exception> unknownIssues = new ArrayList<>();

    final List<SubStats> subStats = new ArrayList<>();

    public Statistics(DeleteTileRange deleteTileRange) {
        this.deleteTileRange = deleteTileRange;
    }

    public boolean completed() {
        return recoverableIssues.isEmpty()
                && nonRecoverableIssues.isEmpty()
                && unknownIssues.isEmpty();
    }

    public List<SubStats> getSubStats() {
        return subStats;
    }

    public void addSubStats(SubStats stats) {
        this.getSubStats().add(stats);
        this.deleted = this.getDeleted() + stats.getDeleted();
        this.processed = this.getProcessed() + stats.getProcessed();
        stats.getRecoverableIssues().forEach(this.recoverableIssues::add);
        stats.getNonRecoverableIssues().forEach(this.nonRecoverableIssues::add);
        stats.getUnknownIssues().forEach(this.unknownIssues::add);
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

}
