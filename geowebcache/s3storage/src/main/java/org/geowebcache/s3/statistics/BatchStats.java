package org.geowebcache.s3.statistics;

import org.geowebcache.s3.delete.DeleteTileRange;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public class BatchStats {
    private final DeleteTileRange deleteTileRange;
    private long deleted;
    private long processed;
    private long bytes;

    public BatchStats(DeleteTileRange deleteTileRange) {
        checkNotNull(deleteTileRange, "deleteTileRange cannot be null");
        this.deleteTileRange = deleteTileRange;
    }

    public void setProcessed(long processed) {
        this.processed = processed;
    }

    public void add(ResultStat stat) {
        if (Objects.requireNonNull(stat.getChange()) == ResultStat.Change.Deleted) {
            deleted += 1;
            bytes += stat.getSize();
        }
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

    public long getBytes() {
        return bytes;
    }
}
