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

import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.S3ObjectPathsForPrefix;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeleteTileParametersBulkDeleteTaskTest {
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
    public void test_ChooseStrategy_S3ObjectPathsForPrefix() {
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileParametersId).build();
        assertThat(
                "Expected S3ObjectPathsForPrefix strategy",
                task.chooseStrategy(deleteTileParametersId),
                is(S3ObjectPathsForPrefix));
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_withCheck() {
        when(s3ObjectsWrapper.iterator())
                .thenAnswer(invocation -> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileParametersId).build();
        Long count = task.call();
        Statistics statistics = callback.getStatistics();
        long expectedProcessed = 4;
        long expectedDeleted = 4;
        long expectedBatches = 1;
        assertEquals("Result should be 1", expectedProcessed, (long) count);
        assertEquals("Should have deleted 1 tile", expectedDeleted, statistics.getDeleted());
        assertEquals("Should have sent one batch", expectedBatches, statistics.getBatchSent());
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

        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileParametersId).build();
        task.call();

        assertEquals("Expected TaskStarted callback called once", 1, callback.getTaskStartedCount());
        assertEquals("Expected TaskEnded callback called once", 1, callback.getTaskEndedCount());
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

        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileParametersId).build();
        task.call();

        assertEquals("Expected SubTaskStarted callback called once", 1, callback.getSubTaskStartedCount());
        assertEquals("Expected SubTaskEnded callback called once", 1, callback.getSubTaskEndedCount());
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

        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileParametersId).build();
        task.call();

        assertEquals("Expected BatchStarted callback called once", 1, callback.getBatchStartedCount());
        assertEquals("Expected BatchEnded  callback called once", 1, callback.getBatchEndedCount());
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

        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileParametersId).build();
        task.call();

        assertEquals("Expected TileResult callback called once", 4, callback.getTileResultCount());
    }

    @Test
    public void testCall_WhenSmallBatchToProcess_DeleteBatchResult_nothingDeleted() {
        when(s3ObjectsWrapper.iterator())
                .thenAnswer(invocation -> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenAnswer(invocationOnMock -> BulkDeleteTaskTestHelper.emptyDeleteObjectsResult());

        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileParametersId).build();
        task.call();

        assertEquals("Expected TileResult not to called", 0, callback.getTileResultCount());
    }
}
