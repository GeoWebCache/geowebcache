package org.geowebcache.s3.delete;

import java.util.List;
import java.util.stream.Stream;

public interface CompositeDeleteTileRange extends DeleteTileRange {
    List<DeleteTileRange> children();

    void add(DeleteTileRange child);

    @Override
    default Stream<DeleteTileRange> stream() {
        return children().stream();
    }
}
