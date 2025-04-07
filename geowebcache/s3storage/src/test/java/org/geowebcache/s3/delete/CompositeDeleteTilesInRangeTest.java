package org.geowebcache.s3.delete;

import org.junit.Test;

import java.util.Optional;

import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.geowebcache.s3.streams.StreamTestHelper.SINGLE_ZOOM_SINGLE_BOUND_MATCHING;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class CompositeDeleteTilesInRangeTest {
    @Test
    public void testConstructor_CompositeDeleteTilesInRange_PrefixSet() {
        CompositeDeleteTilesInRange deleteTilesInRange = new CompositeDeleteTilesInRange(
                PREFIX, BUCKET, LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING);
        assertThat("Prefix was not set", deleteTilesInRange.getPrefix(), is(PREFIX));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_PrefixNull() {
        assertThrows(
                "Expected NullPointerException when prefix is null",
                NullPointerException.class,
                () -> new CompositeDeleteTilesInRange(
                        null, BUCKET, LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_PrefixEmpty() {
        CompositeDeleteTilesInRange deleteTilesInRange = new CompositeDeleteTilesInRange(
                " \t\n", BUCKET, LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING);
        assertThat("Prefix was not set", deleteTilesInRange.getPrefix(), is(""));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_BucketSet() {
        CompositeDeleteTilesInRange deleteTilesInRange = new CompositeDeleteTilesInRange(
                PREFIX, BUCKET, LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING);
        assertThat("Bucket was not set", deleteTilesInRange.getBucket(), is(BUCKET));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_BucketNull() {
        assertThrows(
                "Bucket is missing",
                NullPointerException.class,
                () -> new CompositeDeleteTilesInRange(
                        PREFIX, null, LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_BucketEmpty() {
        assertThrows(
                "Bucket was not set",
                IllegalArgumentException.class,
                () -> new CompositeDeleteTilesInRange(
                        PREFIX, " \t\n", LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_LayerIdSet() {
        CompositeDeleteTilesInRange deleteTilesInRange = new CompositeDeleteTilesInRange(
                PREFIX, BUCKET, LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING);
        assertThat("LayerId was not set", deleteTilesInRange.getLayerId(), is(LAYER_ID));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_LayerIdNull() {
        assertThrows(
                "LayerId is missing",
                NullPointerException.class,
                () -> new CompositeDeleteTilesInRange(
                        PREFIX, BUCKET, null, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_LayerIdEmpty() {
        assertThrows(
                "LayerId is invalid",
                IllegalArgumentException.class,
                () -> new CompositeDeleteTilesInRange(
                        PREFIX, BUCKET, " \t\n", FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_formatSet() {
        CompositeDeleteTilesInRange deleteTilesInRange = new CompositeDeleteTilesInRange(
                PREFIX, BUCKET, LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING);
        assertThat("Format was not set", deleteTilesInRange.getFormat(), is(FORMAT_IN_KEY));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_formatNull() {
        assertThrows(
                "Format is missing",
                NullPointerException.class,
                () -> new CompositeDeleteTilesInRange(
                        PREFIX, BUCKET, LAYER_ID, null, SINGLE_ZOOM_SINGLE_BOUND_MATCHING));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_formatEmpty() {
        assertThrows(
                "format is invalid",
                IllegalArgumentException.class,
                () -> new CompositeDeleteTilesInRange(
                        PREFIX, BUCKET, LAYER_ID, " \t\n", SINGLE_ZOOM_SINGLE_BOUND_MATCHING));
    }

    @Test
    public void testConstructor_CompositeDeleteTilesInRange_tileRangeNull() {
        assertThrows(
                "tileRange is invalid",
                NullPointerException.class,
                () -> new CompositeDeleteTilesInRange(
                        PREFIX, BUCKET, LAYER_ID, FORMAT_IN_KEY, null));
    }

    @Test
    public void test_constructor_singleZoom_singleBound() {
        CompositeDeleteTilesInRange deleteTilesInRange = new CompositeDeleteTilesInRange(
                PREFIX, BUCKET, LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING);
        assertThat("With a single bound in a single zoom level", deleteTilesInRange.children().size(), is(1));
        Optional<DeleteTileRange> possibleDeleteTileRange = deleteTilesInRange.children().stream().findFirst();
        possibleDeleteTileRange.ifPresent(deleteTileRange -> {
                    assertThat("Should be a DeleteTileZoomInBoundedBox", deleteTileRange, is(instanceOf(DeleteTileZoomInBoundedBox.class)));
                    DeleteTileZoomInBoundedBox deleteTileZoomInBoundedBox = (DeleteTileZoomInBoundedBox) deleteTileRange;
                    assertThat("Child should have its prefix set", deleteTileZoomInBoundedBox.getPrefix(), is(PREFIX));
                    assertThat("Child should have its bucket set", deleteTileZoomInBoundedBox.getBucketName(), is(BUCKET));
                    assertThat("Child should have its layer id set", deleteTileZoomInBoundedBox.getLayerId(), is(LAYER_ID));
                    assertThat("Child should have its grid set id set", deleteTileZoomInBoundedBox.getGridSetId(), is(GRID_SET_ID));
                    assertThat("Child should have its format set", deleteTileZoomInBoundedBox.getFormat(), is(FORMAT_IN_KEY));
                    assertThat("Child should have its tileRange set", deleteTileZoomInBoundedBox.getTileRange(), is(SINGLE_ZOOM_SINGLE_BOUND_MATCHING));
                    assertThat("Child should have its zoom level set", deleteTileZoomInBoundedBox.getZoomLevel(), is(ZOOM_LEVEL_4));
                }
        );
    }
}