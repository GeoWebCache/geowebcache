package org.geowebcache.s3.streams;

import org.geowebcache.s3.delete.DeleteTileZoomInBoundedBox;
import org.junit.Test;

import java.util.Objects;
import java.util.stream.Stream;

import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.geowebcache.s3.delete.DeleteTileRangeWithTileRange.ONE_BY_ONE_META_TILING_FACTOR;
import static org.geowebcache.s3.streams.StreamTestHelper.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class TileIteratorSupplierTest {
    @Test
    public void test_next_single_zoom_single_bounded_box() {
        TileIterator tileIterator = new TileIterator(SINGLE_ZOOM_SINGLE_BOUND_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);
        DeleteTileZoomInBoundedBox deleteTileZoomInBoundedBox = new DeleteTileZoomInBoundedBox(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4,
                SMALL_BOUNDED_BOX, SINGLE_ZOOM_SINGLE_BOUND_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);

        TileIteratorSupplier tileIteratorSupplier = new TileIteratorSupplier(tileIterator, deleteTileZoomInBoundedBox);
        assertThat("There are 16 tiles in the small bounded box",
                Stream.generate(tileIteratorSupplier).takeWhile(Objects::nonNull).count(), is(16L));
    }

    // The first bound box per zoom level is used and subsequne one are ignored
    @Test
    public void test_next_single_zoom_multiple_boxes() {
        TileIterator tileIterator = new TileIterator(SINGLE_ZOOM_MULTIPLE_BOUNDS_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);
        DeleteTileZoomInBoundedBox deleteTileZoomInBoundedBox = new DeleteTileZoomInBoundedBox(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, SMALL_BOUNDED_BOX, SINGLE_ZOOM_MULTIPLE_BOUNDS_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);

        TileIteratorSupplier tileIteratorSupplier = new TileIteratorSupplier(tileIterator, deleteTileZoomInBoundedBox);
        assertThat("There are 16 tiles in the small bounded box",
                Stream.generate(tileIteratorSupplier).takeWhile(Objects::nonNull)
                        .count(), is(16L));
    }

    @Test
    public void test_next_multiple_zoom_multiple_boxes() {
        TileIterator tileIterator = new TileIterator(MULTIPLE_ZOOM_SINGLE_BOUND_PER_ZOOM_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);
        DeleteTileZoomInBoundedBox deleteTileZoomInBoundedBox = new DeleteTileZoomInBoundedBox(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, SMALL_BOUNDED_BOX, MULTIPLE_ZOOM_SINGLE_BOUND_PER_ZOOM_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);

        TileIteratorSupplier tileIteratorSupplier = new TileIteratorSupplier(tileIterator, deleteTileZoomInBoundedBox);
        assertThat("There are 16 tiles in each bound box of three small bounded box",
                Stream.generate(tileIteratorSupplier).takeWhile(Objects::nonNull)
                        .count(), is(48L));
    }

    @Test
    public void test_next_singleZoom_singleBound_not_matching() {
        TileIterator tileIterator = new TileIterator(SINGLE_ZOOM_SINGLE_BOUND_NOT_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);
        DeleteTileZoomInBoundedBox deleteTileZoomInBoundedBox = new DeleteTileZoomInBoundedBox(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, SMALL_BOUNDED_BOX, MULTIPLE_ZOOM_SINGLE_BOUND_PER_ZOOM_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);

        TileIteratorSupplier tileIteratorSupplier = new TileIteratorSupplier(tileIterator, deleteTileZoomInBoundedBox);
        assertThrows(
                "When there is no bounding box for the zoom an IllegalArgumentException is thrown",
                IllegalStateException.class,
                () -> Stream.generate(tileIteratorSupplier)
                        .takeWhile(Objects::nonNull)
                        .count());
    }
}