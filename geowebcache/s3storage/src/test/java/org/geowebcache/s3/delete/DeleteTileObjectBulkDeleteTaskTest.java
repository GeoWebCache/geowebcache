package org.geowebcache.s3.delete;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.geowebcache.io.Resource;
import org.geowebcache.s3.AmazonS3Wrapper;
import org.geowebcache.s3.S3ObjectsWrapper;
import org.geowebcache.s3.callback.CaptureCallback;
import org.geowebcache.s3.callback.StatisticCallbackDecorator;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.storage.TileObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Iterator;

import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.SingleTile;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeleteTileObjectBulkDeleteTaskTest {
    @Mock
    public S3ObjectsWrapper s3ObjectsWrapper;

    @Mock
    public AmazonS3Wrapper amazonS3Wrapper;

    @Mock
    public Resource resource;

    public TileObject tileObject;
    private BulkDeleteTask.Builder builder;
    private final CaptureCallback callback = new CaptureCallback(new StatisticCallbackDecorator(LOGGER));

    @Before
    public void setup() {
        tileObject =
                TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, resource);
        builder = BulkDeleteTask.newBuilder()
                .withAmazonS3Wrapper(amazonS3Wrapper)
                .withS3ObjectsWrapper(s3ObjectsWrapper)
                .withBucket(BUCKET)
                .withBatch(BATCH)
                .withLogger(LOGGER)
                .withCallback(callback);
    }

    @Test
    public void test_ChooseStrategy_S3ObjectPathsForPrefix() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        BulkDeleteTask.ObjectPathStrategy strategy = task.chooseStrategy(deleteTileObject);
        assertEquals("Expected SingleTile strategy", SingleTile, strategy);
    }

    @Test
    public void testCall_WhenSingleToProcess_withCheck() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        Long count = task.call();
        Statistics statistics = callback.getStatistics();
        long expectedProcessed = 1;
        long expectedDeleted = 1;
        long expectedBatches = 1;
        assertEquals("Result should be 1", expectedProcessed, (long) count);
        assertEquals("Should have deleted 1 tile", expectedDeleted, statistics.getDeleted());
        assertEquals("Should have sent one batch", expectedBatches, statistics.getBatchSent());
    }

    @Test
    public void testCall_WhenSingleToProcess_skipCheck() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        Long count = task.call();
        Statistics statistics = callback.getStatistics();
        long expectedProcessed = 1;
        long expectedDeleted = 1;
        long expectedBatches = 1;
        assertEquals("Result should be 1", expectedProcessed, (long) count);
        assertEquals("Should have deleted 1 tile", expectedDeleted, statistics.getDeleted());
        assertEquals("Should have sent one batch", expectedBatches, statistics.getBatchSent());
    }

    @Test
    public void testCall_WhenSingleToProcess_checkTaskNotificationCalled() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        task.call();

        assertEquals("Expected TaskStarted callback called once", 1, callback.getTaskStartedCount());
        assertEquals("Expected TaskEnded callback called once", 1, callback.getTaskEndedCount());
    }

    @Test
    public void testCall_WhenSingleToProcess_checkSubTaskNotificationCalled() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        task.call();

        assertEquals("Expected SubTaskStarted callback called once", 1, callback.getSubTaskStartedCount());
        assertEquals("Expected SubTaskEnded callback called once", 1, callback.getSubTaskEndedCount());
    }

    @Test
    public void testCall_WhenSingleToProcess_checkBatchNotificationCalled() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        task.call();

        assertEquals("Expected BatchStarted callback called once", 1, callback.getBatchStartedCount());
        assertEquals("Expected BatchEnded  callback called once", 1, callback.getBatchEndedCount());
    }

    @Test
    public void testCall_WhenSingleToProcess_checkTileNotificationCalled() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        task.call();

        assertEquals("Expected TileResult callback called once", 1, callback.getTileResultCount());
        assertTrue("Expected the number of bytes processed to be greater than 0", callback.getTileResultCount() > 0);
    }

    @Test
    public void testCall_WhenSingleToProcess_DeleteBatchResult_nothingDeleted() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenAnswer(invocationOnMock -> BulkDeleteTaskTestHelper.emptyDeleteObjectsResult());

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        Long count = task.call();

        assertEquals("Expected TileResult not to called", 0, callback.getTileResultCount());
    }
}
