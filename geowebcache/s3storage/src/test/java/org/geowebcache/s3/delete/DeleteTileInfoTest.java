package org.geowebcache.s3.delete;

import static java.lang.String.format;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DeleteTileInfoTest {

    private final DeleteTileInfo.Builder builder = DeleteTileInfo.newBuilder()
            .withPrefix(PREFIX)
            .withLayerId(LAYER_ID)
            .withGridSetId(GRID_SET_ID)
            .withFormat(FORMAT_IN_KEY)
            .withParametersId(PARAMETERS_ID)
            .withX(XYZ[0])
            .withY(XYZ[1])
            .withZ(XYZ[2]);

    @Before
    public void setup() throws Exception {}

    @Test
    public void test_checkLayerIDInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(LAYER_ID, keyMatcher.group(DeleteTileInfo.LAYER_ID_GROUP_POS));
    }

    @Test
    public void test_checkGridSetIDInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(GRID_SET_ID, keyMatcher.group(DeleteTileInfo.GRID_SET_ID_GROUP_POS));
    }

    @Test
    public void test_checkFormatInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertThat(keyMatcher.matches(), is(true));
        assertThat(keyMatcher.group(DeleteTileInfo.TYPE_GROUP_POS), is(FORMAT_IN_KEY));
    }

    @Test
    public void test_checkXInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(XYZ[0], Long.parseLong(keyMatcher.group(DeleteTileInfo.X_GROUP_POS)));
    }

    @Test
    public void test_checkYInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(XYZ[1], Long.parseLong(keyMatcher.group(DeleteTileInfo.Y_GROUP_POS)));
    }

    @Test
    public void test_checkZInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(XYZ[2], Long.parseLong(keyMatcher.group(DeleteTileInfo.Z_GROUP_POS)));
    }

    @Test
    public void test_checkFromS3ObjectKey() {
        var testData = List.of(new TestHelper(
                "Valid case",
                "prefix/layer-id/EPSG:4326/png/75595e9159afae9c4669aee57366de8c196a57e1/3/1/2.png",
                PREFIX,
                LAYER_ID,
                GRID_SET_ID,
                FORMAT_IN_KEY,
                PARAMETERS_ID,
                XYZ[0],
                XYZ[1],
                XYZ[2]));

        testData.forEach(data -> {
            if (!Objects.nonNull(data.err)) {
                DeleteTileInfo keyObject = DeleteTileInfo.fromObjectPath(data.objectKey);
                assertEquals(data.name, data.prefix, keyObject.prefix);
                assertEquals(data.name, data.parameterSha, keyObject.parametersSha);
                assertEquals(data.name, data.layerId, keyObject.layerId);
                assertEquals(data.name, data.gridSetId, keyObject.gridSetId);
                assertEquals(data.name, data.format, keyObject.format);
                assertEquals(data.name, data.x, keyObject.x);
                assertEquals(data.name, data.y, keyObject.y);
                assertEquals(data.name, data.z, keyObject.z);
            } else {
                assertThrows(data.name, data.err.getClass(), () -> DeleteTileInfo.fromObjectPath(data.objectKey));
            }
        });
    }

    @Test
    public void test_isPathValid_layerId() {
        String path = format("%s/", LAYER_ID);
        assertThat("layer_id path is valid", DeleteTileInfo.isPathValid(path, PREFIX), is(true));
    }

    @Test
    public void test_isPathValid_gridSetId() {
        String path = format("%s/%s/", LAYER_ID, GRID_SET_ID);
        assertThat("grid_set_if path is valid", DeleteTileInfo.isPathValid(path, PREFIX), is(true));
    }

    @Test
    public void test_isPathValid_gridSetId_missingLayerId() {
        String path = format("%s/%s/", "", GRID_SET_ID);
        assertThat(
                "grid_set_if path is invalid when layerId is missing",
                DeleteTileInfo.isPathValid(path, PREFIX),
                is(false));
    }

    @Test
    public void test_isPathValid_gridSetId_missingGridSetId() {
        String path = format("%s/%s/", LAYER_ID, "");
        assertThat(
                "grid_set_if path is invalid when gridSetId is missing",
                DeleteTileInfo.isPathValid(path, PREFIX),
                is(false));
    }

    @Test
    public void test_isPathValid_format() {
        String path = format("%s/%s/%s/", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY);
        assertThat("format path is valid", DeleteTileInfo.isPathValid(path, PREFIX), is(true));
    }

    @Test
    public void test_isPathValid_format_missingLayerId() {
        String path = format("%s/%s/%s/", "", GRID_SET_ID, FORMAT_IN_KEY);
        assertThat(
                "format path is invalid when layerId is missing", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_format_missingGridSetId() {
        String path = format("%s/%s/%s/", LAYER_ID, "", FORMAT_IN_KEY);
        assertThat(
                "format path is invalid when gridSetId is missing",
                DeleteTileInfo.isPathValid(path, PREFIX),
                is(false));
    }

    @Test
    public void test_isPathValid_format_missingFormat() {
        String path = format("%s/%s/%s/", LAYER_ID, GRID_SET_ID, "");
        assertThat(
                "format path is invalid when format is missing", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_parametersId() {
        String path = format("%s/%s/%s/%s/", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID);
        assertThat("format path is valid", DeleteTileInfo.isPathValid(path, PREFIX), is(true));
    }

    @Test
    public void test_isPathValid_parametersId_missingLayerId() {
        String path = format("%s/%s/%s/%s/", "", GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID);
        assertThat(
                "format path is invalid when layerId is missing", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_parametersId_missingGridSetId() {
        String path = format("%s/%s/%s/%s/", LAYER_ID, "", FORMAT_IN_KEY, PARAMETERS_ID);
        assertThat(
                "format path is invalid when gridSetId is missing",
                DeleteTileInfo.isPathValid(path, PREFIX),
                is(false));
    }

    @Test
    public void test_isPathValid_parametersId_missingFormat() {
        String path = format("%s/%s/%s/%s/", LAYER_ID, GRID_SET_ID, "", PARAMETERS_ID);
        assertThat(
                "format path is invalid when format is missing", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_parametersId_missingParametersId() {
        String path = format("%s/%s/%s/%s/", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, "");
        assertThat(
                "format path is invalid when format is missing", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_zoom() {
        String path = format("%s/%s/%s/%s/%d/", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4);
        assertThat("format path is valid", DeleteTileInfo.isPathValid(path, PREFIX), is(true));
    }

    @Test
    public void test_isPathValid_zoom_notANumber() {
        String path = format("%s/%s/%s/%s/%s/", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, "notANumber");
        assertThat(
                "format path is invalid when z is not a number", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_zoom_missingLayerId() {
        String path = format("%s/%s/%s/%s/%d/", "", GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4);
        assertThat(
                "format path is invalid when layerId is missing", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_zoom_missingGridSetId() {
        String path = format("%s/%s/%s/%s/%d/", LAYER_ID, "", FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4);
        assertThat(
                "format path is invalid when gridSetId is missing",
                DeleteTileInfo.isPathValid(path, PREFIX),
                is(false));
    }

    @Test
    public void test_isPathValid_zoom_missingFormat() {
        String path = format("%s/%s/%s/%s/%d/", LAYER_ID, GRID_SET_ID, "", PARAMETERS_ID, ZOOM_LEVEL_4);
        assertThat(
                "format path is invalid when format is missing", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_zoom_missingParametersId() {
        String path = format("%s/%s/%s/%s/%d/", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, "", ZOOM_LEVEL_4);
        assertThat(
                "format path is invalid when format is missing", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_zoom_missingZoom() {
        String path = format("%s/%s/%s/%s/%s/", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, "");
        assertThat(
                "format path is invalid when format is missing", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_x() {
        String path =
                format("%s/%s/%s/%s/%d/%d/", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, XYZ[0]);
        assertThat("format path is valid", DeleteTileInfo.isPathValid(path, PREFIX), is(true));
    }

    @Test
    public void test_isPathValid_x_notANumber() {
        String path = format(
                "%s/%s/%s/%s/%d/%s/", LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, "notANumber");
        assertThat(
                "format path is invalid when x is not a number", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_y() {
        String path = format(
                "%s/%s/%s/%s/%d/%d/%d",
                LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, XYZ[0], XYZ[1]);
        assertThat("format path is valid", DeleteTileInfo.isPathValid(path, PREFIX), is(true));
    }

    @Test
    public void test_isPathValid_y_notANumber() {
        String path = format(
                "%s/%s/%s/%s/%d/%d/%s",
                LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, XYZ[0], "notANumber");
        assertThat(
                "format path is invalid when y is not a number", DeleteTileInfo.isPathValid(path, PREFIX), is(false));
    }

    @Test
    public void test_isPathValid_yWithExtension() {
        String path = format(
                "%s/%s/%s/%s/%d/%d/%d.%s",
                LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, XYZ[0], XYZ[1], EXTENSION);
        assertThat("path with extension is valid", DeleteTileInfo.isPathValid(path, PREFIX), is(true));
    }

    @Test
    public void test_isPathValid_yWithExtension_notANumber() {
        String path = format(
                "%s/%s/%s/%s/%d/%d/%s.%s",
                LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, XYZ[0], "notANumber", EXTENSION);
        assertThat(
                "path with extension invalid when y is not a number",
                DeleteTileInfo.isPathValid(path, PREFIX),
                is(false));
    }

    @Test
    public void test_isPathValid_yWithExtension_whenExtensionMissing() {
        String path = format(
                "%s/%s/%s/%s/%d/%d/%d.%s",
                LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4, XYZ[0], XYZ[1], "");
        assertThat(
                "path with extension invalid when extension is missing",
                DeleteTileInfo.isPathValid(path, PREFIX),
                is(false));
    }

    static class TestHelper {
        final String name;
        final String objectKey;
        final String prefix;
        final String layerId;
        final String gridSetId;
        final String format;
        final String parameterSha;
        final long x;
        final long y;
        final long z;
        final RuntimeException err;

        public TestHelper(
                String name,
                String objectKey,
                String prefix,
                String layerId,
                String gridSetId,
                String format,
                String parameterSha,
                long x,
                long y,
                long z,
                RuntimeException err) {
            this.name = name;
            this.prefix = prefix;
            this.objectKey = objectKey;
            this.layerId = layerId;
            this.gridSetId = gridSetId;
            this.format = format;
            this.parameterSha = parameterSha;
            this.x = x;
            this.y = y;
            this.z = z;
            this.err = err;
        }

        public TestHelper(
                String name,
                String objectKey,
                String prefix,
                String layerId,
                String gridSetId,
                String format,
                String parameterSha,
                long x,
                long y,
                long z) {
            this.name = name;
            this.objectKey = objectKey;
            this.prefix = prefix;
            this.layerId = layerId;
            this.gridSetId = gridSetId;
            this.format = format;
            this.parameterSha = parameterSha;
            this.x = x;
            this.y = y;
            this.z = z;
            this.err = null;
        }
    }
}
