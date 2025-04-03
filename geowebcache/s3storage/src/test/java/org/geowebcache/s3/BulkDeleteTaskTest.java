package org.geowebcache.s3;

import static org.geowebcache.s3.BulkDeleteTask.ObjectPathStrategy.DefaultStrategy;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.geowebcache.mime.MimeType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BulkDeleteTaskTest extends TestCase {
    private static final String LAYER_ID = "layer";
    private static final long X1 = 1;
    private static final long Y1 = 1;
    private static final long X2 = 1;
    private static final long Y2 = 1;
    private static final String GRID_SET_ID = "grid_set_id";
    private static final String FORMAT_IN_KEY = "png";
    private static final String PARAMETER_SHA = "75595e9159afae9c4669aee57366de8c196a57e1";
    private static final String PARAMETER_1_KEY = "key1";
    private static final String PARAMETER_1_VALUE = "value1";
    private static final Map<String, String> PARAMETERS = new HashMap<>();
    private static final int ZOOM_0 = 0;
    private static final int ZOOM_1 = 1;
    private static final int ZOOM_2 = 2;
    private static final int ZOOM_START = ZOOM_0;
    private static final int ZOOM_END = ZOOM_2;

    // Range bounds format: {{minx, maxx, miny, maxy, zoomLevel}, ...}
    private static final long[][] RANGE_BOUNDS = {
        {X1, X2, Y1, Y2, ZOOM_0}, {X1 * 2, X2 * 2, Y1 * 2, Y2 * 2, ZOOM_1}, {X1 * 4, X2 * 4, Y1 * 4, Y2 * 4, ZOOM_2}
    };
    private static final String BUCKET = "test-bucket";
    private static final long TIMESTAMP = System.currentTimeMillis();
    private static final String LAYER_NAME =  "LayerName";

    static {
        // FIND Wha
        PARAMETERS.put(PARAMETER_1_KEY, PARAMETER_1_VALUE);
    }

    @Mock
    public S3ObjectsWrapper s3ObjectsWrapper;

    @Mock
    public AmazonS3Wrapper amazonS3Wrapper;

    private DeleteTileRange deleteTileRange;
    private MimeType mimeType;
    private BulkDeleteTask.Builder builder;
    ;

    private static final List<S3ObjectSummary> S_3_OBJECT_SUMMARY_LIST = new ArrayList<>();
    private static final List<S3ObjectSummary> S_3_OBJECT_EMPTY_SUMMARY_LIST = new ArrayList<>();
    private static final S3ObjectSummary SUMMARY_1 = new S3ObjectSummary();
    private static final S3ObjectSummary SUMMARY_2 = new S3ObjectSummary();
    private static final S3ObjectSummary SUMMARY_3 = new S3ObjectSummary();

    static {
        SUMMARY_1.setKey("key");
        S_3_OBJECT_SUMMARY_LIST.add(SUMMARY_1);
        S_3_OBJECT_SUMMARY_LIST.add(SUMMARY_2);
        S_3_OBJECT_SUMMARY_LIST.add(SUMMARY_3);
    }

    @Before
    public void setup() throws Exception {

        builder = BulkDeleteTask.newBuilder()
                .withAmazonS3Wrapper(amazonS3Wrapper)
                .withS3ObjectsWrapper(s3ObjectsWrapper)
                .withBucket(BUCKET)
                .withBatch(3)
                .withLoggingCallback();
    }

    @Test
    public void testCall_ReturnsZeroCount_WhenNoTilesToProcess() throws Exception {
        when(s3ObjectsWrapper.iterator()).thenReturn(S_3_OBJECT_EMPTY_SUMMARY_LIST.iterator());

        var task = builder.withDeleteRange(DeleteTileLayer.newBuilder()
                        .withLayerName(LAYER_NAME)
                        .withLayerId(LAYER_ID)
                        .withBucket(BUCKET)
                        .build())
                .build();
        var count = task.call();
        assertEquals("Should be no tiles to process", 0, (long) count);
    }

    @Test
    public void test_ChooseStrategy_defaultReturned() {
        var task = builder.withDeleteRange(DeleteTileLayer.newBuilder()
                        .withLayerName(LAYER_NAME)
                        .withLayerId(LAYER_ID)
                        .withBucket(BUCKET)
                        .build())
                .build();
        var strategy = task.chooseStrategy();
        assertEquals("Expected default strategy", DefaultStrategy, strategy);
    }
}
