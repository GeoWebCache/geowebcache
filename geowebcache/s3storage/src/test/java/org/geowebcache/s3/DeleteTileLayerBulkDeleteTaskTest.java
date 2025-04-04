package org.geowebcache.s3;

import static org.geowebcache.s3.BulkDeleteTask.ObjectPathStrategy.S3ObjectPathsForPrefix;
import static org.geowebcache.s3.BulkDeleteTaskTestHelper.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.Iterator;

import org.geowebcache.s3.BulkDeleteTask.LoggingCallback;
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
    private final CaptureCallback callback = new CaptureCallback(new LoggingCallback());

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
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request =
                    (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME))
                .build();
        Long count = task.call();
        BulkDeleteTask.Statistics statistics = callback.statistics;
        assertEquals(
                "Should have batch large summary collection size", S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST.size(), (long) count);
        assertEquals(
                "Should have deleted large summary collection size",
                S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST.size(),
                statistics.deleted);
        assertEquals(
                "Should have batch large summary collection size",
                S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST.size(),
                statistics.processed);
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
        BulkDeleteTask.Statistics statistics = callback.statistics;
        assertEquals("Should have processed large summary collection size", S_3_OBJECT_SUMMARY_LARGE_LIST.size(), (long)
                count);
        assertEquals(
                "Should have deleted large summary collection size",
                S_3_OBJECT_SUMMARY_LARGE_LIST.size(),
                statistics.deleted);
        assertEquals(
                "Should have processed large summary collection size",
                S_3_OBJECT_SUMMARY_LARGE_LIST.size(),
                statistics.processed);
    }
}
