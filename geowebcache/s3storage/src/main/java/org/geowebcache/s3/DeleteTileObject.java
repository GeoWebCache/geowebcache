package org.geowebcache.s3;


import com.google.common.base.Preconditions;
import org.geowebcache.storage.TileObject;

import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class DeleteTileObject implements  DeleteTileRange {
    private final TileObject tileObject;
    private final String prefix;
    private final boolean skipExistsCheck;

    public DeleteTileObject(TileObject tileObject, String prefix, boolean skipExistsCheck) {
        checkNotNull(tileObject, "tileObject must not be null");
        checkNotNull(prefix, "prefix must not be null");

        this.tileObject = tileObject;
        this.prefix = prefix;
        this.skipExistsCheck = skipExistsCheck;
    }

    @Override
    public String path() {
        return prefix;
    }

    @Override
    public Stream<DeleteTileRange> stream() {
        return Stream.of(this);
    }

    public TileObject getTileObject() {
        return tileObject;
    }
    public boolean shouldSkipExistsCheck() {
        return skipExistsCheck;
    }
}
