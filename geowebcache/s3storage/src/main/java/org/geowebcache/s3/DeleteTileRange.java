package org.geowebcache.s3;

import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public interface DeleteTileRange {
    String path();
    default Stream<DeleteTileRange> stream() {
        return Stream.of(this);
    }
}

