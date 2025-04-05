package org.geowebcache.s3.streams;

import java.util.Iterator;
import org.geowebcache.storage.TileObject;

public class BoundedBoxTileIterator implements Iterator<TileObject> {
    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public TileObject next() {
        return null;
    }
}
