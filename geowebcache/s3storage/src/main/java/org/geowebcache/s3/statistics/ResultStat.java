package org.geowebcache.s3.statistics;

import org.geowebcache.s3.delete.DeleteTileRange;
import org.geowebcache.storage.TileObject;

public class ResultStat {
    private final DeleteTileRange deleteTileRange;
    private final String path;
    private final TileObject tileObject; // Can be null?
    private final long size;
    private final long when;
    private final Change change;

    public ResultStat(DeleteTileRange deleteTileRange, String path, TileObject tileObject, long size, long when, Change change) {
        this.deleteTileRange = deleteTileRange;
        this.path = path;
        this.tileObject = tileObject;
        this.size = size;
        this.when = when;
        this.change = change;
    }

    public DeleteTileRange getDeleteTileRange() {
        return deleteTileRange;
    }

    public String getPath() {
        return path;
    }

    public TileObject getTileObject() {
        return tileObject;
    }

    public long getSize() {
        return size;
    }

    public long getWhen() {
        return when;
    }

    public Change getChange() {
        return change;
    }

    public enum Change {
        Deleted
    }
}
