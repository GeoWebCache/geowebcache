package org.geowebcache.s3;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.stream.Stream;
import org.geowebcache.storage.TileObject;

public class DeleteTileObject implements DeleteTileRange {
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
