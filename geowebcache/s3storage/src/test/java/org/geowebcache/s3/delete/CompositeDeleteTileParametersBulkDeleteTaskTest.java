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

import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CompositeDeleteTileParametersBulkDeleteTaskTest {
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
    public void testCall_WhenSmallBatchToProcess() {
        when(s3ObjectsWrapper.iterator())
                .thenAnswer(invocation -> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        CompositeDeleteTileParameterId compositeDeleteTileParameterId = ALL_GRIDS_ALL_FORMATS_COMPOSITE_TILE_PARAMETERS;
        BulkDeleteTask task =
                builder.withDeleteRange(compositeDeleteTileParameterId).build();
        Long count = task.call();
        Statistics statistics = callback.getStatistics();

        long subTasks = compositeDeleteTileParameterId.children().size();
        assertThat("As the batch is one hundred one batch per sub task", statistics.getBatchSent(), is(subTasks));
        long processed = subTasks * S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().size();
        assertThat("The task.call() return the number of tiles processed", count, is(processed));
        assertThat("All are processed", statistics.getProcessed(), is(processed));
        assertThat("All are deleted", statistics.getDeleted(), is(processed));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkTaskNotificationCalled() {
        when(s3ObjectsWrapper.iterator())
                .thenAnswer(invocation -> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });
        BulkDeleteTask task = builder.withDeleteRange(ALL_GRIDS_ALL_FORMATS_COMPOSITE_TILE_PARAMETERS)
                .build();
        task.call();

        assertThat("Expected TaskStarted callback called once", callback.getTaskStartedCount(), is(1L));
        assertThat("Expected TaskEnded callback called once", callback.getTaskEndedCount(), is(1L));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkSubTaskNotificationCalled() {
        when(s3ObjectsWrapper.iterator())
                .thenAnswer(invocation -> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task = builder.withDeleteRange(ALL_GRIDS_ALL_FORMATS_COMPOSITE_TILE_PARAMETERS)
                .build();
        task.call();

        assertThat("Expected SubTaskStarted callback called per subtask", callback.getSubTaskStartedCount(), is(4L));
        assertThat("Expected SubTaskEnded callback called per subtask", callback.getSubTaskEndedCount(), is(4L));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkBatchNotificationCalled() {
        when(s3ObjectsWrapper.iterator())
                .thenAnswer(invocation -> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task = builder.withDeleteRange(ALL_GRIDS_ALL_FORMATS_COMPOSITE_TILE_PARAMETERS)
                .build();
        task.call();

        assertThat("Expected one batch per subtask for small single batches", callback.getBatchStartedCount(), is(4L));
        assertThat("Expected one batch per subtask for small single batches", callback.getBatchEndedCount(), is(4L));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_checkTileNotificationCalled() {
        when(s3ObjectsWrapper.iterator())
                .thenAnswer(invocation -> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task = builder.withDeleteRange(ALL_GRIDS_ALL_FORMATS_COMPOSITE_TILE_PARAMETERS)
                .build();
        task.call();

        long processed = (long) ALL_GRIDS_ALL_FORMATS_COMPOSITE_TILE_PARAMETERS
                        .children()
                        .size()
                * S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().size();
        assertThat(
                "Expected TileResult callback called once per processed tile",
                callback.getTileResultCount(),
                is(processed));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_DeleteBatchResult_nothingDeleted() {
        when(s3ObjectsWrapper.iterator())
                .thenAnswer(invocation -> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenAnswer(invocationOnMock -> BulkDeleteTaskTestHelper.emptyDeleteObjectsResult());

        BulkDeleteTask task = builder.withDeleteRange(ALL_GRIDS_ALL_FORMATS_COMPOSITE_TILE_PARAMETERS)
                .build();
        task.call();

        assertThat("Expected TileResult not to called", callback.getTileResultCount(), is(0L));
    }
}
