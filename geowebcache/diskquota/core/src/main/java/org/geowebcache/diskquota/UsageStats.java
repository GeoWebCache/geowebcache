package org.geowebcache.diskquota;

import java.util.Arrays;

import org.geowebcache.diskquota.storage.TileSet;

public class UsageStats {

    private final TileSet tileSet;

    private final long[] tileIndex;

    public UsageStats(TileSet tileset, long[] tileIndex) {
        this.tileSet = tileset;
        this.tileIndex = tileIndex;
    }

    public TileSet getTileSet() {
        return tileSet;
    }

    public long[] getTileIndex() {
        return tileIndex;
    }

    @Override
    public String toString() {
        return new StringBuilder("[").append(tileSet.toString()).append(", ")
                .append(Arrays.toString(tileIndex)).append("]").toString();
    }
}