package org.geowebcache.s3.delete;

import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.S3ObjectPathsForPrefix;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
public class DeleteTileLayerBulkDeleteTaskTest {
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
        DeleteTileLayer deleteTileRange = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileRange).build();
        BulkDeleteTask.ObjectPathStrategy strategy = task.chooseStrategy(deleteTileRange);
        assertEquals("Expected default strategy", S3ObjectPathsForPrefix, strategy);
    }

    @Test
    public void testCall_WhenBatchOrLessToProcess() throws Exception {
        when(s3ObjectsWrapper.iterator())
                .thenAnswer(invocation -> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME))
                .build();
        Long count = task.call();
        Statistics statistics = callback.getStatistics();
        assertEquals(
                "Should have batch large summary collection size",
                S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().size(),
                (long) count);
        assertEquals(
                "Should have deleted large summary collection size",
                S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().size(),
                statistics.getDeleted());
        assertEquals(
                "Should have batch large summary collection size",
                S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST().size(),
                statistics.getProcessed());
    }

    @Test
    public void testCall_WhenMoreThanBatchToProcess() throws Exception {
        when(s3ObjectsWrapper.iterator()).thenReturn(S_3_OBJECT_SUMMARY_LARGE_LIST.iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME))
                .build();
        Long count = task.call();
        Statistics statistics = callback.getStatistics();
        assertEquals("Should have processed large summary collection size", S_3_OBJECT_SUMMARY_LARGE_LIST.size(), (long)
                count);
        assertEquals(
                "Should have deleted large summary collection size",
                S_3_OBJECT_SUMMARY_LARGE_LIST.size(),
                statistics.getDeleted());
        assertEquals(
                "Should have processed large summary collection size",
                S_3_OBJECT_SUMMARY_LARGE_LIST.size(),
                statistics.getProcessed());
    }
}
