package org.geowebcache.s3;

import org.geowebcache.storage.TileObject;

import java.util.Iterator;

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
