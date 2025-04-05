package org.geowebcache.s3.statistics;

import org.geowebcache.storage.TileObject;

public class ResultStat {
    private String path;
    private TileObject tileObject;  // Can be null?
    private long size;
    private long when;

    public ResultStat(String path, TileObject tileObject, long size, long when) {
        this.path = path;
        this.tileObject = tileObject;
        this.size = size;
        this.when = when;
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

}
