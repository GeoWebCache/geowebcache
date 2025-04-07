package org.geowebcache.s3.delete;

import org.junit.Test;

import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class DeleteTileParametersIdTest {
    @Test
    public void testConstructor_DeleteTileParametersId_PrefixSet() {
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        assertThat("Prefix was not set", deleteTileParametersId.getPrefix(), is(PREFIX));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_PrefixNull() {
        assertThrows(
                NullPointerException.class,
                () -> new DeleteTileParametersId(
                        null, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_PrefixEmpty() {
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                " \t\n", BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        assertThat("Prefix was not set", deleteTileParametersId.getPrefix(), is(""));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_BucketSet() {
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        assertThat("Bucket was not set", deleteTileParametersId.getBucket(), is(BUCKET));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_BucketNull() {
        assertThrows(
                "Bucket is missing",
                NullPointerException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, null, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_BucketEmpty() {
        assertThrows(
                "Bucket is invalid",
                IllegalArgumentException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, " \t\n", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_LayerIdSet() {
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        assertThat("LayerId was not set", deleteTileParametersId.getLayerId(), is(LAYER_ID));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_LayerIdNull() {
        assertThrows(
                "LayerId is missing",
                NullPointerException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, null, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_LayerIdEmpty() {
        assertThrows(
                "LayerId is invalid",
                IllegalArgumentException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, " \t\n", GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_GridSetIdSet() {
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        assertThat("GridSetId was not set", deleteTileParametersId.getGridSetId(), is(GRID_SET_ID));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_GridSetIdNull() {
        assertThrows(
                "GridSetId is missing",
                NullPointerException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, LAYER_ID, null, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_GridSetIdEmpty() {
        assertThrows(
                "GridSetId is invalid",
                IllegalArgumentException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, LAYER_ID, " \t\n", FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_FormatSet() {
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        assertThat("Format was not set", deleteTileParametersId.getFormat(), is(FORMAT_IN_KEY));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_FormatNull() {
        assertThrows(
                "Format is missing",
                NullPointerException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, null, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_FormatEmpty() {
        assertThrows(
                "Format is invalid",
                IllegalArgumentException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, " \t\n", PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_ParametersIdSet() {
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        assertThat("ParametersId was not set", deleteTileParametersId.getParameterId(), is(PARAMETERS_ID));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_ParametersIdMissing() {
        assertThrows(
                "ParametersId is missing",
                NullPointerException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, null, LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_ParametersIdEmpty() {
        assertThrows(
                "ParametersId is invalid",
                IllegalArgumentException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, " \t\n", LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_LayerNameSet() {
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        assertThat("LayerName was not set", deleteTileParametersId.getLayerName(), is(LAYER_NAME));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_LayerNameNull() {
        assertThrows(
                "LayerName is missing",
                NullPointerException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, null));
    }

    @Test
    public void testConstructor_DeleteTileParametersId_LayerNameEmpty() {
        assertThrows(
                "LayerName is invalid",
                IllegalArgumentException.class,
                () -> new DeleteTileParametersId(
                        PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, " \t\n"));
    }
}
