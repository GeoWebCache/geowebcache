package org.geowebcache.s3.delete;

import java.util.stream.Stream;

public interface DeleteTileRange {
    String path();

    default Stream<DeleteTileRange> stream() {
        return Stream.of(this);
    }
}
