package org.geowebcache.s3.delete;

import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.S3ObjectPathsForPrefix;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.geowebcache.s3.streams.StreamTestHelper.SINGLE_ZOOM_SINGLE_BOUND_MATCHING;
import static org.junit.Assert.assertEquals;

import org.geowebcache.s3.AmazonS3Wrapper;
import org.geowebcache.s3.S3ObjectsWrapper;
import org.geowebcache.s3.callback.CaptureCallback;
import org.geowebcache.s3.callback.StatisticCallbackDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DeleteTileZoomBulkDeleteTest {
    @Mock
    public S3ObjectsWrapper s3ObjectsWrapper;

    @Mock
    public AmazonS3Wrapper amazonS3Wrapper;

    private BulkDeleteTask.Builder builder;
    private final CaptureCallback callback = new CaptureCallback(new StatisticCallbackDecorator(LOGGER));

    @Before
    public void setup() {
        builder = BulkDeleteTask.newBuilder()
                .withAmazonS3Wrapper(amazonS3Wrapper)
                .withS3ObjectsWrapper(s3ObjectsWrapper)
                .withBucket(BUCKET)
                .withBatch(BATCH)
                .withLogger(LOGGER)
                .withCallback(callback);
    }

    @Test
    public void test_ChooseStrategy_RetryPendingTask() {
        DeleteTileZoom deleteTileZoomInBoundedBox = new DeleteTileZoom(
                PREFIX,
                BUCKET,
                LAYER_ID,
                GRID_SET_ID,
                FORMAT_IN_KEY,
                PARAMETERS_ID,
                ZOOM_LEVEL_4,
                SINGLE_ZOOM_SINGLE_BOUND_MATCHING);
        BulkDeleteTask task =
                builder.withDeleteRange(deleteTileZoomInBoundedBox).build();
        BulkDeleteTask.ObjectPathStrategy strategy = task.chooseStrategy(deleteTileZoomInBoundedBox);
        assertEquals("Expected S3ObjectPathsForPrefix strategy", S3ObjectPathsForPrefix, strategy);
    }
}
