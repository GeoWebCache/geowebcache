package org.geowebcache.s3;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.geowebcache.io.Resource;
import org.geowebcache.s3.BulkDeleteTask.LoggingCallback;
import org.geowebcache.storage.TileObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Iterator;

import static org.geowebcache.s3.BulkDeleteTask.ObjectPathStrategy.SingleTile;
import static org.geowebcache.s3.BulkDeleteTaskTestHelper.*;
import static org.junit.Assert.assertEquals;
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
    private final CaptureCallback callback = new CaptureCallback(new LoggingCallback());

    @Before
    public void setup() {
        tileObject = TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, resource);
        builder = BulkDeleteTask.newBuilder()
                .withAmazonS3Wrapper(amazonS3Wrapper)
                .withS3ObjectsWrapper(s3ObjectsWrapper)
                .withBucket(BUCKET)
                .withBatch(BATCH)
                .withCallback(callback);
    }

    @Test
    public void test_ChooseStrategy_S3ObjectPathsForPrefix() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX, false);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject).build();
        BulkDeleteTask.ObjectPathStrategy strategy = task.chooseStrategy(deleteTileObject);
        assertEquals("Expected SingleTile strategy", SingleTile, strategy);
    }

    @Test
    public void testCall_WhenBatchOrLessToProcess_withCheck() throws Exception {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX, false);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject)
                .build();
        Long count = task.call();
        BulkDeleteTask.Statistics statistics = callback.statistics;
        long expectedProcessed = 1;
        long expectedDeleted = 1;
        long expectedBatches = 1;
        assertEquals("Result should be 1", expectedProcessed, (long) count);
        assertEquals("Should have deleted 1 tile", expectedDeleted, statistics.deleted);
        assertEquals("Should have sent one batch", expectedBatches, statistics.batchSent);
    }

    @Test
    public void testCall_WhenBatchOrLessToProcess_skipCheck() throws Exception {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX, true);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileObject)
                .build();
        Long count = task.call();
        BulkDeleteTask.Statistics statistics = callback.statistics;
        long expectedProcessed = 1;
        long expectedDeleted = 1;
        long expectedBatches = 1;
        assertEquals("Result should be 1", expectedProcessed, (long) count);
        assertEquals("Should have deleted 1 tile", expectedDeleted, statistics.deleted);
        assertEquals("Should have sent one batch", expectedBatches, statistics.batchSent);
    }
}
