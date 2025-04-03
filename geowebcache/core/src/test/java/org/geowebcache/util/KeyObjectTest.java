package org.geowebcache.util;

import static org.junit.Assert.assertThrows;

import java.util.*;
import java.util.regex.Matcher;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KeyObjectTest extends TestCase {

    private static final String PREFIX = "Store";
    private static final String LAYER_NAME = "layer";
    private static final String LAYER_ID = "layer_id";
    private static final long X = 1;
    private static final long Y = 1;
    private static final long Z = 1;
    long[] X_Y_Z = {X, Y, Z};
    private static final String GRID_SET_ID = "grid_set_id";
    private static final String FORMAT = "image/png";
    private static final String FORMAT_IN_KEY = "png";
    private static final String PARAMETER_SHA = "75595e9159afae9c4669aee57366de8c196a57e1";
    private static final String PARAMETER_1_KEY = "key1";
    private static final String PARAMETER_1_VALUE = "value1";
    private static final Map<String, String> PARAMETERS = new HashMap<>();

    static {
        // FIND Wha
        PARAMETERS.put(PARAMETER_1_KEY, PARAMETER_1_VALUE);
    }

    private KeyObject.Builder builder = KeyObject.newBuilder().
            withPrefix(PREFIX).
            withLayerId(LAYER_ID).
            withGridSetId(GRID_SET_ID).
            withFormat(FORMAT_IN_KEY).
            withParametersId(PARAMETER_SHA).
            withX(X).
            withY(Y).
            withZ(Z);

    @Before
    public void setup() throws Exception {
    }

    @Test
    public void test_checkLayerIDInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = KeyObject.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(LAYER_ID, keyMatcher.group(KeyObject.LAYER_ID_GROUP_POS));
    }

    @Test
    public void test_checkGridSetIDInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = KeyObject.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(GRID_SET_ID, keyMatcher.group(KeyObject.GRID_SET_ID_GROUP_POS));
    }

    @Test
    public void test_checkFormatInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = KeyObject.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(FORMAT_IN_KEY, keyMatcher.group(KeyObject.TYPE_GROUP_POS));
    }

    @Test
    public void test_checkXInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = KeyObject.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(X, Long.parseLong(keyMatcher.group(KeyObject.X_GROUP_POS)));
    }

    @Test
    public void test_checkYInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = KeyObject.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(Y, Long.parseLong(keyMatcher.group(KeyObject.Y_GROUP_POS)));
    }

    @Test
    public void test_checkZInKey() {
        var result = builder.build().objectPath();

        Matcher keyMatcher = KeyObject.keyRegex.matcher(result);
        assertTrue(keyMatcher.matches());
        assertEquals(Z, Long.parseLong(keyMatcher.group(KeyObject.Z_GROUP_POS)));
    }

    @Test
    public void test_checkFromS3ObjectKey() {
        var testData = Arrays.asList(
                new KeyObjectTest.TestHelper(
                        "Valid case",
                        "Store/layer_id/grid_set_id/png/75595e9159afae9c4669aee57366de8c196a57e1/1/1/1.png",
                        PREFIX, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETER_SHA, X, Y, Z
                )
        );

        testData.forEach(data -> {
            if (!Objects.nonNull(data.err)) {
                KeyObject keyObject = KeyObject.fromObjectPath(data.objectKey);
                assertEquals(data.name, data.prefix, keyObject.prefix);
                assertEquals(data.name, data.parameterSha, keyObject.parametersSha);
                assertEquals(data.name, data.layerId, keyObject.layerId);
                assertEquals(data.name, data.gridSetId, keyObject.gridSetId);
                assertEquals(data.name, data.format, keyObject.format);
                assertEquals(data.name, data.x, keyObject.x);
                assertEquals(data.name, data.y, keyObject.y);
                assertEquals(data.name, data.z, keyObject.z);
            } else {
                assertThrows(data.name, data.err.getClass(), () -> KeyObject.fromObjectPath(data.objectKey));
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
