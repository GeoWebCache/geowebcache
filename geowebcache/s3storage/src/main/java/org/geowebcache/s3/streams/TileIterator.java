package org.geowebcache.s3.streams;

import com.google.common.collect.AbstractIterator;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.TileRangeIterator;

public class TileIterator extends AbstractIterator<long[]> {
    private final TileRangeIterator trIter;
    private final TileRange tileRange;

    public TileIterator(TileRange tileRange, int[] metaTilingFactors) {
        this.tileRange = tileRange;
        this.trIter = new TileRangeIterator(tileRange, metaTilingFactors);
    }

    @Override
    protected long[] computeNext() {
        long[] gridLoc = trIter.nextMetaGridLocation(new long[3]);
        return gridLoc == null ? endOfData() : gridLoc;
    }

    public TileRange getTileRange() {
        return tileRange;
    }
}
