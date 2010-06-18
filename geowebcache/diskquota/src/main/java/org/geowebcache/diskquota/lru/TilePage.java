package org.geowebcache.diskquota.lru;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class TilePage {

    /**
     * x, y, z index for this page, in reverse order (z, y, x) to aid in a more efficient short
     * circuit comparison at {@link #equals(Object)}
     */
    private final int[] zyxIndex;

    private AtomicLong numHits;

    public TilePage(final int x, final int y, final int z) {
        this(x, y, z, 0);
    }

    public TilePage(final int x, final int y, final int z, final long numHits) {
        this.zyxIndex = new int[] { z, y, x };
        this.numHits = new AtomicLong(numHits);
    }

    public long markHit() {
        long hits = numHits.incrementAndGet();
        return hits;
    }

    public long getNumHits() {
        long hits = numHits.get();
        return hits;
    }

    public int getX() {
        return zyxIndex[2];
    }

    public int getY() {
        return zyxIndex[1];
    }

    public int getZ() {
        return zyxIndex[0];
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TilePage)) {
            return false;
        }
        return Arrays.equals(zyxIndex, ((TilePage) o).zyxIndex);
    }

    @Override
    public int hashCode() {
        return 17 * (zyxIndex[0] + zyxIndex[1] ^ 2 + zyxIndex[2] ^ 3);
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append("[").append(zyxIndex[2])
                .append(',').append(zyxIndex[1]).append(',').append(zyxIndex[0]).append(". Hits: ")
                .append(getNumHits()).append(']').toString();
    }
}
