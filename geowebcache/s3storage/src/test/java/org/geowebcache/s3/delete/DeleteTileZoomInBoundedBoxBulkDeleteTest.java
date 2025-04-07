package org.geowebcache.s3.delete;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import org.geowebcache.s3.AmazonS3Wrapper;
import org.geowebcache.s3.S3ObjectsWrapper;
import org.geowebcache.s3.callback.CaptureCallback;
import org.geowebcache.s3.callback.StatisticCallbackDecorator;
import org.geowebcache.s3.statistics.Statistics;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.TileRangeWithBoundedBoxIfTileExist;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.geowebcache.s3.delete.DeleteTileRangeWithTileRange.ONE_BY_ONE_META_TILING_FACTOR;
import static org.geowebcache.s3.streams.StreamTestHelper.SINGLE_ZOOM_SINGLE_BOUND_MATCHING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeleteTileZoomInBoundedBoxBulkDeleteTest {
    @Mock
    public S3ObjectsWrapper s3ObjectsWrapper;

    @Mock
    public AmazonS3Wrapper amazonS3Wrapper;

    private BulkDeleteTask.Builder builder;
    private final CaptureCallback callback = new CaptureCallback(new StatisticCallbackDecorator());

    @Before
    public void setup() {
        builder = BulkDeleteTask.newBuilder()
                .withAmazonS3Wrapper(amazonS3Wrapper)
                .withS3ObjectsWrapper(s3ObjectsWrapper)
                .withBucket(BUCKET)
                .withBatch(BATCH)
                .withCallback(callback);
    }

    @Test
    public void test_ChooseStrategy_RetryPendingTask() {
        DeleteTileZoomInBoundedBox deleteTileZoomInBoundedBox =
                new DeleteTileZoomInBoundedBox(
                        PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4,
                        SMALL_BOUNDED_BOX, SINGLE_ZOOM_SINGLE_BOUND_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileZoomInBoundedBox).build();
        BulkDeleteTask.ObjectPathStrategy strategy = task.chooseStrategy(deleteTileZoomInBoundedBox);
        assertEquals("Expected SingleTile strategy", TileRangeWithBoundedBoxIfTileExist, strategy);
    }

    @Test
    public void testCall_WhenSmallBatchToProcess() {
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task =
                builder.withDeleteRange(SINGLE_ZOOM_SINGLE_BOUND_DELETE_TILES_IN_RANGE).build();
        Long count = task.call();
        Statistics statistics = callback.getStatistics();

        assertThat("As the batch is one hundred one batch per sub task", statistics.getBatchSent(), is(1L));
        long processed = SINGLE_ZOOM_SINGLE_BOUND_TILES;
        assertThat("The task.call() return the number of tiles processed", count, is(processed));
        assertThat("All are processed", statistics.getProcessed(), is(processed));
        assertThat("All are deleted", statistics.getDeleted(), is(processed));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkTaskNotificationCalled() {
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task =
                builder.withDeleteRange(SINGLE_ZOOM_SINGLE_BOUND_DELETE_TILES_IN_RANGE).build();
        task.call();

        assertThat("Expected TaskStarted callback called once", callback.getTaskStartedCount(), is(1L));
        assertThat("Expected TaskEnded callback called once", callback.getTaskEndedCount(), is(1L));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkSubTaskNotificationCalled() {
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task =
                builder.withDeleteRange(SINGLE_ZOOM_SINGLE_BOUND_DELETE_TILES_IN_RANGE).build();
        task.call();

        long subTasks = 1L;
        assertThat("Expected SubTaskStarted callback called per subtask", callback.getSubTaskStartedCount(), is(subTasks));
        assertThat("Expected SubTaskEnded callback called per subtask", callback.getSubTaskEndedCount(), is(subTasks));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkBatchNotificationCalled() {
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task =
                builder.withDeleteRange(SINGLE_ZOOM_SINGLE_BOUND_DELETE_TILES_IN_RANGE).build();
        task.call();

        long batches = SINGLE_ZOOM_SINGLE_BOUND_TILES / BATCH + 1;

        assertThat("Expected one batch per subtask for small single batches", callback.getBatchStartedCount(), is(batches));
        assertThat("Expected one batch per subtask for small single batches", callback.getBatchEndedCount(), is(batches));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkTileNotificationCalled() {
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task =
                builder.withDeleteRange(SINGLE_ZOOM_SINGLE_BOUND_DELETE_TILES_IN_RANGE).build();
        task.call();

        long subTasks = 1L;
        long processed = subTasks * SINGLE_ZOOM_SINGLE_BOUND_TILES;

        assertThat(
                "Expected TileResult callback called once per processed tile",
                callback.getTileResultCount(),
                is(processed));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_DeleteBatchResult_nothingDeleted() {
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenAnswer(invocationOnMock -> BulkDeleteTaskTestHelper.emptyDeleteObjectsResult());

        BulkDeleteTask task =
                builder.withDeleteRange(SINGLE_ZOOM_SINGLE_BOUND_DELETE_TILES_IN_RANGE).build();
        task.call();

        assertThat("Expected TileResult not to called", callback.getTileResultCount(), is(0L));
        verify(amazonS3Wrapper, times(1)).deleteObjects(any(DeleteObjectsRequest.class));
    }

}
