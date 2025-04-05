package org.geowebcache.s3.statistics;

import static com.google.common.base.Preconditions.checkNotNull;

import org.geowebcache.s3.delete.DeleteTileRange;

public class BatchStats {
    private DeleteTileRange deleteTileRange;
    private long deleted;
    private long processed;

    public BatchStats(DeleteTileRange deleteTileRange) {
        checkNotNull(deleteTileRange, "deleteTileRange cannot be null");
        this.deleteTileRange = deleteTileRange;
    }

    public void setProcessed(long processed) {
        this.processed = processed;
    }

    public void add(ResultStat stat) {
        deleted += 1;
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
}
