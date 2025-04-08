package org.geowebcache.s3.delete;

import org.geowebcache.storage.TileRange;

public interface DeleteTileRangeWithTileRange extends DeleteTileRange {
    TileRange getTileRange();

    int[] getMetaTilingFactor();

    int[] ONE_BY_ONE_META_TILING_FACTOR = {1, 1};

    // When iterating over a parameter range all of these are available
    String getLayerId();

    String getPrefix();

    String getGridSetId();

    String getFormat();

    String getParametersId();
}
