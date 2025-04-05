package org.geowebcache.s3.delete;

public class DeleteTestHelper {
    public static final DeleteTileRange DELETE_TILE_RANGE = new DummyDeleteTileRange();

    public static class DummyDeleteTileRange implements DeleteTileRange {
        @Override
        public String path() {
            return "dummy/";
        }
    }
}
