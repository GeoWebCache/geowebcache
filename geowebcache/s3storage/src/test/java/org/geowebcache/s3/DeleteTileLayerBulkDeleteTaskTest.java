package org.geowebcache.s3;

import static org.geowebcache.s3.BulkDeleteTask.ObjectPathStrategy.DefaultStrategy;
import static org.geowebcache.s3.BulkDeleteTask.ObjectPathStrategy.S3ObjectPathsForPrefix;
import static org.geowebcache.s3.BulkDeleteTaskTestHelper.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import junit.framework.TestCase;
import org.geowebcache.s3.BulkDeleteTask.LoggingCallback;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Iterator;

@RunWith(MockitoJUnitRunner.class)
public class DeleteTileLayerBulkDeleteTaskTest extends TestCase {
    private static final String PATH_WITH_PREFIX = "prefix/layer-id/";
    private static final String PATH_WITHOUT_PREFIX = "/layer-id/";

    @Mock
    public S3ObjectsWrapper s3ObjectsWrapper;

    @Mock
    public AmazonS3Wrapper amazonS3Wrapper;

    private BulkDeleteTask.Builder builder;
    private final CaptureCallback callback = new CaptureCallback(new LoggingCallback());

    @Before
    public void setup() throws Exception {
        builder = BulkDeleteTask.newBuilder()
                .withAmazonS3Wrapper(amazonS3Wrapper)
                .withS3ObjectsWrapper(s3ObjectsWrapper)
                .withBucket(BUCKET)
                .withBatch(BATCH)
                .withCallback(callback);
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_TaskNotNull() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        assertNotNull(task);
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_AmazonS3Wrapper() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        assertEquals("AmazonS3Wrapper was not set", amazonS3Wrapper, task.getAmazonS3Wrapper());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_S3ObjectsWrapper() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        assertEquals("S3ObjectsWrapper was not set", s3ObjectsWrapper, task.getS3ObjectsWrapper());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_Bucket() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        assertEquals("Bucket was not set", BUCKET, task.getBucketName());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_Batch() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        assertEquals("Batch was not set", BATCH, task.getBatch());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_Callback() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        assertEquals("Callback was not set", callback, task.getCallback());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_PrefixSet() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        DeleteTileLayer deleteTileLayer = (DeleteTileLayer) task.getDeleteTileRange();
        assertEquals("Prefix was not set", PREFIX, deleteTileLayer.getPrefix());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_BucketSet() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        DeleteTileLayer deleteTileLayer = (DeleteTileLayer) task.getDeleteTileRange();
        assertEquals("Bucket was not set", BUCKET, deleteTileLayer.getBucket());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_LayerId() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        DeleteTileLayer deleteTileLayer = (DeleteTileLayer) task.getDeleteTileRange();
        assertEquals("LayerId was not set", LAYER_ID, deleteTileLayer.getLayerId());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_LayerName() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        DeleteTileLayer deleteTileLayer = (DeleteTileLayer) task.getDeleteTileRange();
        assertEquals("LayerName was not set", LAYER_NAME, deleteTileLayer.getLayerName());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_PathWithPrefix() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        DeleteTileLayer deleteTileLayer = (DeleteTileLayer) task.getDeleteTileRange();
        assertEquals("Path with prefix is wrong", PATH_WITH_PREFIX, deleteTileLayer.path());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_PathWithoutPrefix() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer("", BUCKET, LAYER_ID, LAYER_NAME)).build();
        DeleteTileLayer deleteTileLayer = (DeleteTileLayer) task.getDeleteTileRange();
        assertEquals("Path without prefix is wrong", PATH_WITHOUT_PREFIX, deleteTileLayer.path());
    }

    @Test
    public void test_ChooseStrategy_S3ObjectPathsForPrefix() {
        DeleteTileLayer deleteTileRange = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        var task = builder.withDeleteRange(deleteTileRange).build();
        var strategy = task.chooseStrategy(deleteTileRange);
        assertEquals("Expected default strategy", S3ObjectPathsForPrefix, strategy);
    }

    @Test
    public void testCall_WhenBatchOrLessToProcess() throws Exception {
        Iterator<S3ObjectSummary> iterator = S_3_OBJECT_SUMMARY_BATCH_LIST.iterator();
        when(s3ObjectsWrapper.iterator()).thenReturn(iterator);
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request = (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        Long count = task.call();
        BulkDeleteTask.Statistics statistics = callback.statistics;
        assertEquals("Should have batch large summary collection size", S_3_OBJECT_SUMMARY_BATCH_LIST.size(), (long) count);
        assertEquals("Should have deleted large summary collection size", S_3_OBJECT_SUMMARY_BATCH_LIST.size(), statistics.deleted);
        assertEquals("Should have batch large summary collection size", S_3_OBJECT_SUMMARY_BATCH_LIST.size(), statistics.processed);
    }

    @Test
    public void testCall_WhenMoreThanBatchToProcess() throws Exception {
        when(s3ObjectsWrapper.iterator()).thenReturn(S_3_OBJECT_SUMMARY_LARGE_LIST.iterator());
        when(amazonS3Wrapper.deleteObjects(any(DeleteObjectsRequest.class))).thenAnswer(invocationOnMock -> {
            DeleteObjectsRequest request = (DeleteObjectsRequest) invocationOnMock.getArguments()[0];
            return BulkDeleteTaskTestHelper.generateDeleteObjectsResult(request);
        });

        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME)).build();
        Long count = task.call();
        BulkDeleteTask.Statistics statistics = callback.statistics;
        assertEquals("Should have processed large summary collection size", S_3_OBJECT_SUMMARY_LARGE_LIST.size(), (long) count);
        assertEquals("Should have deleted large summary collection size", S_3_OBJECT_SUMMARY_LARGE_LIST.size(), statistics.deleted);
        assertEquals("Should have processed large summary collection size", S_3_OBJECT_SUMMARY_LARGE_LIST.size(), statistics.processed);
    }


}
