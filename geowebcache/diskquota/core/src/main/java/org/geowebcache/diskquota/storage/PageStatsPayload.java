package org.geowebcache.diskquota.storage;

import org.springframework.util.Assert;

public class PageStatsPayload {

    private int numTiles;

    private long lastAccessTime;

    private int numHits;

    private final TilePage page;

    public PageStatsPayload(final TilePage page) {
        Assert.notNull(page, "Page can't be null");
        this.page = page;
    }

    public TilePage getPage() {
        return page;
    }

    public int getNumTiles() {
        return numTiles;
    }

    public void setNumTiles(int numTiles) {
        this.numTiles = numTiles;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public int getNumHits() {
        return numHits;
    }

    public void setNumHits(int numHits) {
        this.numHits = numHits;
    }

}
