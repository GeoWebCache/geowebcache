package org.geowebcache.s3.delete;

import static java.lang.String.format;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class DeleteTilePrefixTest {

    @Test
    public void testDeleteTilePrefix_constructor_canCreateAnInstance() {
        String path =
                DeleteTileInfo.toZoomPrefix(PREFIX, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4);
        DeleteTilePrefix deleteTilePrefix = new DeleteTilePrefix(PREFIX, BUCKET, path);
        assertThat("Expected instance to be constructed for a partial path", deleteTilePrefix, is(notNullValue()));
    }

    @Test
    public void testDeleteTilePrefix_constructor_withACompletePath() {
        String path = DeleteTileInfo.toFullPath(
                PREFIX, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, XYZ[0], XYZ[1], EXTENSION);
        DeleteTilePrefix deleteTilePrefix = new DeleteTilePrefix(PREFIX, BUCKET, path);
        assertThat("Expected instance to be constructed for a full path", deleteTilePrefix, is(notNullValue()));
    }

    @Test
    public void testDeleteTilePrefix_constructor_prefixCannotBeNull() {
        String path =
                DeleteTileInfo.toZoomPrefix(PREFIX, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4);

        assertThrows(
                "Prefix cannot be null", NullPointerException.class, () -> new DeleteTilePrefix(null, BUCKET, path));
    }

    @Test
    public void testDeleteTilePrefix_constructor_bucketCannotBeNull() {
        String path =
                DeleteTileInfo.toZoomPrefix(PREFIX, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4);

        assertThrows(
                "Bucket cannot be null", NullPointerException.class, () -> new DeleteTilePrefix(PREFIX, null, path));
    }

    @Test
    public void testDeleteTilePrefix_constructor_pathCannotBeNull() {
        assertThrows(
                "Path cannot be null", NullPointerException.class, () -> new DeleteTilePrefix(PREFIX, BUCKET, null));
    }

}
