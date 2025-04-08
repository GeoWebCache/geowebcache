package org.geowebcache.s3.delete;

import static java.lang.String.format;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.geowebcache.s3.delete.DeleteTileInfo.EXTENSION_GROUP_POS;
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

    @Before
    public void setup() throws Exception {}

    @Test
    public void test_checkLayerIDInKey() {
        String result = new DeleteTileInfo(
                        PREFIX,
                        LAYER_ID,
                        GRID_SET_ID,
                        FORMAT_IN_KEY,
                        PARAMETERS_ID,
                        XYZ[0],
                        XYZ[1],
                        XYZ[2],
                        null,
                        null,
                        EXTENSION)
                .objectPath();
        ;

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue("Regex does not match " + result, keyMatcher.matches());
        assertEquals(LAYER_ID, keyMatcher.group(DeleteTileInfo.LAYER_ID_GROUP_POS));
    }

    @Test
    public void test_checkGridSetIDInKey() {
        String result = new DeleteTileInfo(
                        PREFIX,
                        LAYER_ID,
                        GRID_SET_ID,
                        FORMAT_IN_KEY,
                        PARAMETERS_ID,
                        XYZ[0],
                        XYZ[1],
                        XYZ[2],
                        null,
                        null,
                        EXTENSION)
                .objectPath();
        ;

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue("Regex does not match " + result, keyMatcher.matches());
        assertEquals(GRID_SET_ID, keyMatcher.group(DeleteTileInfo.GRID_SET_ID_GROUP_POS));
    }

    @Test
    public void test_checkFormatInKey() {
        String result = new DeleteTileInfo(
                        PREFIX,
                        LAYER_ID,
                        GRID_SET_ID,
                        FORMAT_IN_KEY,
                        PARAMETERS_ID,
                        XYZ[0],
                        XYZ[1],
                        XYZ[2],
                        null,
                        null,
                        EXTENSION)
                .objectPath();
        ;

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue("Regex does not match " + result, keyMatcher.matches());
        assertThat(keyMatcher.group(DeleteTileInfo.TYPE_GROUP_POS), is(FORMAT_IN_KEY));
    }

    @Test
    public void test_checkXInKey() {
        String result = new DeleteTileInfo(
                        PREFIX,
                        LAYER_ID,
                        GRID_SET_ID,
                        FORMAT_IN_KEY,
                        PARAMETERS_ID,
                        XYZ[0],
                        XYZ[1],
                        XYZ[2],
                        null,
                        null,
                        EXTENSION)
                .objectPath();
        ;

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue("Regex does not match " + result, keyMatcher.matches());
        assertEquals(XYZ[0], Long.parseLong(keyMatcher.group(DeleteTileInfo.X_GROUP_POS)));
    }

    @Test
    public void test_checkYInKey() {
        String result = new DeleteTileInfo(
                        PREFIX,
                        LAYER_ID,
                        GRID_SET_ID,
                        FORMAT_IN_KEY,
                        PARAMETERS_ID,
                        XYZ[0],
                        XYZ[1],
                        XYZ[2],
                        null,
                        null,
                        EXTENSION)
                .objectPath();
        ;

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue("Regex does not match " + result, keyMatcher.matches());
        assertEquals(
                "Regex does not match " + result, XYZ[1], Long.parseLong(keyMatcher.group(DeleteTileInfo.Y_GROUP_POS)));
    }

    @Test
    public void test_checkZInKey() {
        String result = new DeleteTileInfo(
                        PREFIX,
                        LAYER_ID,
                        GRID_SET_ID,
                        FORMAT_IN_KEY,
                        PARAMETERS_ID,
                        XYZ[0],
                        XYZ[1],
                        XYZ[2],
                        null,
                        null,
                        EXTENSION)
                .objectPath();
        ;

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue("Regex does not match " + result, keyMatcher.matches());
        assertEquals(
                "Regex does not match " + result, XYZ[2], Long.parseLong(keyMatcher.group(DeleteTileInfo.Z_GROUP_POS)));
    }

    @Test
    public void test_checkExtensionInKey() {
        String result = new DeleteTileInfo(
                        PREFIX,
                        LAYER_ID,
                        GRID_SET_ID,
                        FORMAT_IN_KEY,
                        PARAMETERS_ID,
                        XYZ[0],
                        XYZ[0],
                        XYZ[1],
                        XYZ[2],
                        null,
                        EXTENSION)
                .objectPath();
        ;

        Matcher keyMatcher = DeleteTileInfo.keyRegex.matcher(result);
        assertTrue("Regex does not match " + result, keyMatcher.matches());
        assertEquals(EXTENSION, keyMatcher.group(EXTENSION_GROUP_POS));
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
