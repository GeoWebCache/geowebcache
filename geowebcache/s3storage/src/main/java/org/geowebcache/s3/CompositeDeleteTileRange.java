package org.geowebcache.s3;

import java.util.List;
import java.util.stream.Stream;

public interface CompositeDeleteTileRange extends DeleteTileRange {
    List<DeleteTileRange> children();

    void add(DeleteTileRange child);

    default Stream<DeleteTileRange> stream() {
        return children().stream();
    }
}
