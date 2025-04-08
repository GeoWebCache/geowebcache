package org.geowebcache.s3.delete;

import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.SingleTile;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.BATCH;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.BUCKET;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.FORMAT_IN_KEY;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.GRID_SET_ID;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.LAYER_NAME;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.LOGGER;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.PARAMETERS;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.PREFIX;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.XYZ;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.generateDeleteObjectsResult;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.FILE_SIZE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.Iterator;
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
        tileObject.setBlobSize(FILE_SIZE.intValue());
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
        assertThat("Expected SingleTile strategy", strategy, is(SingleTile));
    }

    @Test
    public void testCall_WhenSingleToProcess_withCheck() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        Long count = task.call();
        Statistics statistics = callback.getStatistics();
        long expectedProcessed = 1;
        long expectedDeleted = 1;
        long expectedBatches = 1;
        assertThat("Result should be 1", count, is(expectedProcessed));
        assertThat("Should have deleted 1 tile", statistics.getDeleted(), is(expectedDeleted));
        assertThat("Should have sent one batch", statistics.getBatchSent(), is(expectedBatches));
    }

    @Test
    public void testCall_WhenSingleToProcess_checkTaskNotificationCalled() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        task.call();

        assertThat("Expected TaskStarted callback called once", callback.getTaskStartedCount(), is(1L));
        assertThat("Expected TaskEnded callback called once", callback.getTaskEndedCount(), is(1L));
    }

    @Test
    public void testCall_WhenSingleToProcess_checkSubTaskNotificationCalled() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        task.call();

        assertThat("Expected SubTaskStarted callback called once", callback.getSubTaskStartedCount(), is(1L));
        assertThat("Expected SubTaskEnded callback called once", callback.getSubTaskEndedCount(), is(1L));
    }

    @Test
    public void testCall_WhenSingleToProcess_checkBatchNotificationCalled() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        task.call();

        assertThat("Expected BatchStarted callback called once", callback.getBatchStartedCount(), is(1L));
        assertThat("Expected BatchEnded  callback called once", callback.getBatchEndedCount(), is(1L));
    }

    @Test
    public void testCall_WhenSingleToProcess_checkTileNotificationCalled() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        task.call();

        assertThat("Expected TileResult callback called once", callback.getTileResultCount(), is(1L));
        long bytesDeleted = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.stream()
                .mapToLong(S3ObjectSummary::getSize)
                .sum();
        assertThat("Expected the number of bytes processed to correct", callback.getBytes(), is(bytesDeleted));
    }

    @Test
    public void testCall_WhenSingleToProcess_DeleteBatchResult_nothingDeleted() {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenAnswer(invocationOnMock -> BulkDeleteTaskTestHelper.emptyDeleteObjectsResult());

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        task.call();

        assertThat("Expected TileResult not to called", callback.getTileResultCount(), is(0L));
        assertThat("Expected the number of bytes processed to be 0", callback.getBytes(), is(0L));
    }
}
