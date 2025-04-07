package org.geowebcache.s3.delete;

import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

@RunWith(MockitoJUnitRunner.class)
public class CompositeDeleteTileInRangeBulkDeleteTaskTest {
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
    public void testCall_WhenSmallBatchToProcess() {
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        CompositeDeleteTilesInRange compositeDeleteTileInRange =
                SINGLE_ZOOM_SINGLE_BOUND_COMPOSITE_DELETE_TILES_IN_RANGE;
        BulkDeleteTask task =
                builder.withDeleteRange(compositeDeleteTileInRange).build();
        Long count = task.call();
        Statistics statistics = callback.getStatistics();

        long subTasks = compositeDeleteTileInRange.children().size();
        assertThat("As the batch is one hundred one batch per sub task", statistics.getBatchSent(), is(subTasks));
        long processed = subTasks * SINGLE_ZOOM_SINGLE_BOUND_TILES;
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

        BulkDeleteTask task = builder.withDeleteRange(SINGLE_ZOOM_SINGLE_BOUND_COMPOSITE_DELETE_TILES_IN_RANGE)
                .build();
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

        CompositeDeleteTilesInRange singleZoomSingleBoundCompositeDeleteTilesInRange =
                SINGLE_ZOOM_SINGLE_BOUND_COMPOSITE_DELETE_TILES_IN_RANGE;
        BulkDeleteTask task = builder.withDeleteRange(singleZoomSingleBoundCompositeDeleteTilesInRange)
                .build();
        task.call();

        long subTasks =
                singleZoomSingleBoundCompositeDeleteTilesInRange.children().size();
        assertThat(
                "Expected SubTaskStarted callback called per subtask", callback.getSubTaskStartedCount(), is(subTasks));
        assertThat("Expected SubTaskEnded callback called per subtask", callback.getSubTaskEndedCount(), is(subTasks));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkBatchNotificationCalled() {
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        CompositeDeleteTilesInRange singleZoomSingleBoundCompositeDeleteTilesInRange =
                SINGLE_ZOOM_SINGLE_BOUND_COMPOSITE_DELETE_TILES_IN_RANGE;
        BulkDeleteTask task = builder.withDeleteRange(singleZoomSingleBoundCompositeDeleteTilesInRange)
                .build();
        task.call();

        long batches =
                singleZoomSingleBoundCompositeDeleteTilesInRange.children().size()
                        * (SINGLE_ZOOM_SINGLE_BOUND_TILES / BATCH + 1);

        assertThat(
                "Expected one batch per subtask for small single batches",
                callback.getBatchStartedCount(),
                is(batches));
        assertThat(
                "Expected one batch per subtask for small single batches", callback.getBatchEndedCount(), is(batches));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkTileNotificationCalled() {
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        CompositeDeleteTilesInRange singleZoomSingleBoundCompositeDeleteTilesInRange =
                SINGLE_ZOOM_SINGLE_BOUND_COMPOSITE_DELETE_TILES_IN_RANGE;
        BulkDeleteTask task = builder.withDeleteRange(singleZoomSingleBoundCompositeDeleteTilesInRange)
                .build();
        task.call();

        long subTasks =
                singleZoomSingleBoundCompositeDeleteTilesInRange.children().size();
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

        BulkDeleteTask task = builder.withDeleteRange(SINGLE_ZOOM_SINGLE_BOUND_COMPOSITE_DELETE_TILES_IN_RANGE)
                .build();
        task.call();

        assertThat("Expected TileResult not to called", callback.getTileResultCount(), is(0L));
        verify(amazonS3Wrapper, times(1)).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
