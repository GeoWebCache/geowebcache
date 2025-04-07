package org.geowebcache.s3.delete;

import org.junit.Test;

import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class CompositeDeleteTileParameterIdTest {

    @Test
    public void test_constructor_createsAnInstance() {
        CompositeDeleteTileParameterId compositeDeleteTileParameterId = new CompositeDeleteTileParameterId(
                PREFIX, BUCKET, LAYER_ID, SINGLE_SET_OF_GRID_SET_IDS, SINGLE_SET_OF_FORMATS, PARAMETERS_ID, LAYER_NAME);
        assertThat(
                "Expected an instance of CompositeDeleteTileParameterId to be created",
                compositeDeleteTileParameterId,
                is(instanceOf(CompositeDeleteTileParameterId.class)));
    }

    @Test
    public void test_constructor_prefixCannotBeNull() {
        assertThrows(
                "Prefix cannot be null",
                NullPointerException.class,
                () -> new CompositeDeleteTileParameterId(
                        null,
                        BUCKET,
                        LAYER_ID,
                        SINGLE_SET_OF_GRID_SET_IDS,
                        SINGLE_SET_OF_FORMATS,
                        PARAMETERS_ID,
                        LAYER_NAME));
    }

    @Test
    public void test_constructor_prefixCanEmpty() {
        CompositeDeleteTileParameterId compositeDeleteTileParameterId = new CompositeDeleteTileParameterId(
                "", BUCKET, LAYER_ID, SINGLE_SET_OF_GRID_SET_IDS, SINGLE_SET_OF_FORMATS, PARAMETERS_ID, LAYER_NAME);
        assertThat(
                "Expected an instance of CompositeDeleteTileParameterId to be created",
                compositeDeleteTileParameterId,
                is(instanceOf(CompositeDeleteTileParameterId.class)));
    }

    @Test
    public void test_constructor_bucketCannotBeNull() {
        assertThrows(
                "bucket cannot be null",
                NullPointerException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX,
                        null,
                        LAYER_ID,
                        SINGLE_SET_OF_GRID_SET_IDS,
                        SINGLE_SET_OF_FORMATS,
                        PARAMETERS_ID,
                        LAYER_NAME));
    }

    @Test
    public void test_constructor_bucketCannotBeEmpty() {
        assertThrows(
                "bucket cannot be null",
                IllegalArgumentException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX,
                        " \t\n",
                        LAYER_ID,
                        SINGLE_SET_OF_GRID_SET_IDS,
                        SINGLE_SET_OF_FORMATS,
                        PARAMETERS_ID,
                        LAYER_NAME));
    }

    @Test
    public void test_constructor_layerIdCannotBeNull() {
        assertThrows(
                "layer id cannot be null",
                NullPointerException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX,
                        BUCKET,
                        null,
                        SINGLE_SET_OF_GRID_SET_IDS,
                        SINGLE_SET_OF_FORMATS,
                        PARAMETERS_ID,
                        LAYER_NAME));
    }

    @Test
    public void test_constructor_layerIdCannotBeEmpty() {
        assertThrows(
                "layer id cannot be null",
                IllegalArgumentException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX,
                        BUCKET,
                        " \n\t",
                        SINGLE_SET_OF_GRID_SET_IDS,
                        SINGLE_SET_OF_FORMATS,
                        PARAMETERS_ID,
                        LAYER_NAME));
    }

    @Test
    public void test_constructor_gridSetIdsCannotBeNull() {
        assertThrows(
                "grid set ids cannot be null",
                NullPointerException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX, BUCKET, LAYER_ID, null, SINGLE_SET_OF_FORMATS, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void test_constructor_gridSetIdsCannotBeEmpty() {
        assertThrows(
                "grid set ids cannot be null",
                IllegalArgumentException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX,
                        BUCKET,
                        LAYER_ID,
                        EMPTY_SET_OF_GRID_SET_IDS,
                        SINGLE_SET_OF_FORMATS,
                        PARAMETERS_ID,
                        LAYER_NAME));
    }

    @Test
    public void test_constructor_gridSetIdManyGridIds() {
        CompositeDeleteTileParameterId compositeDeleteTileParameterId = new CompositeDeleteTileParameterId(
                PREFIX, BUCKET, LAYER_ID, ALL_SET_OF_GRID_SET_IDS, SINGLE_SET_OF_FORMATS, PARAMETERS_ID, LAYER_NAME);
        assertThat(
                "One child per GridSetId",
                compositeDeleteTileParameterId.children().size(),
                is(ALL_SET_OF_GRID_SET_IDS.size()));
    }

    @Test
    public void test_constructor_formatsCannotBeNull() {
        assertThrows(
                "formats cannot be null",
                NullPointerException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX, BUCKET, LAYER_ID, SINGLE_SET_OF_GRID_SET_IDS, null, PARAMETERS_ID, LAYER_NAME));
    }

    @Test
    public void test_constructor_formatsCannotBeEmpty() {
        assertThrows(
                "formats cannot be null",
                IllegalArgumentException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX,
                        BUCKET,
                        LAYER_ID,
                        SINGLE_SET_OF_GRID_SET_IDS,
                        EMPTY_SET_OF_FORMATS,
                        PARAMETERS_ID,
                        LAYER_NAME));
    }

    @Test
    public void test_constructor_formatsManyFormats() {
        CompositeDeleteTileParameterId compositeDeleteTileParameterId = new CompositeDeleteTileParameterId(
                PREFIX, BUCKET, LAYER_ID, SINGLE_SET_OF_GRID_SET_IDS, ALL_SET_OF_FORMATS, PARAMETERS_ID, LAYER_NAME);
        assertThat(
                "One child per format",
                compositeDeleteTileParameterId.children().size(),
                is(ALL_SET_OF_FORMATS.size()));
    }

    @Test
    public void test_constructor_withManyFormatsAndManyGridSetIds() {
        CompositeDeleteTileParameterId compositeDeleteTileParameterId = new CompositeDeleteTileParameterId(
                PREFIX, BUCKET, LAYER_ID, ALL_SET_OF_GRID_SET_IDS, ALL_SET_OF_FORMATS, PARAMETERS_ID, LAYER_NAME);
        assertThat(
                "One child per format per gridId",
                compositeDeleteTileParameterId.children().size(),
                is(ALL_SET_OF_FORMATS.size() * ALL_SET_OF_GRID_SET_IDS.size()));
    }

    @Test
    public void test_constructor_parametersIdCannotBeNull() {
        assertThrows(
                "ParametersId cannot be null",
                NullPointerException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX, BUCKET, LAYER_ID, SINGLE_SET_OF_GRID_SET_IDS, SINGLE_SET_OF_FORMATS, null, LAYER_NAME));
    }

    @Test
    public void test_constructor_parametersIdCannotBeEmpty() {
        assertThrows(
                "ParametersId cannot be null",
                IllegalArgumentException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX,
                        BUCKET,
                        LAYER_ID,
                        SINGLE_SET_OF_GRID_SET_IDS,
                        SINGLE_SET_OF_FORMATS,
                        " \t\n",
                        LAYER_NAME));
    }

    @Test
    public void test_constructor_layerNameCannotBeNull() {
        assertThrows(
                "Layer name cannot be null",
                NullPointerException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX,
                        BUCKET,
                        LAYER_ID,
                        SINGLE_SET_OF_GRID_SET_IDS,
                        SINGLE_SET_OF_FORMATS,
                        PARAMETERS_ID,
                        null));
    }

    @Test
    public void test_constructor_layerNameCannotBeBlank() {
        assertThrows(
                "Layer name cannot be null",
                IllegalArgumentException.class,
                () -> new CompositeDeleteTileParameterId(
                        PREFIX,
                        BUCKET,
                        LAYER_ID,
                        SINGLE_SET_OF_GRID_SET_IDS,
                        SINGLE_SET_OF_FORMATS,
                        PARAMETERS_ID,
                        " \t\n"));
    }
}
