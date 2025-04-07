package org.geowebcache.s3.delete;

import org.geowebcache.s3.AmazonS3Wrapper;
import org.geowebcache.s3.S3ObjectsWrapper;
import org.geowebcache.s3.callback.CaptureCallback;
import org.geowebcache.s3.callback.StatisticCallbackDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class BulkDeleteTaskTest {
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
    public void testConstructor_WithDeleteTileLayer_TaskNotNull() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME))
                .build();
        assertNotNull(task);
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_AmazonS3Wrapper() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME))
                .build();
        assertEquals("AmazonS3Wrapper was not set", amazonS3Wrapper, task.getAmazonS3Wrapper());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_S3ObjectsWrapper() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME))
                .build();
        assertEquals("S3ObjectsWrapper was not set", s3ObjectsWrapper, task.getS3ObjectsWrapper());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_Bucket() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME))
                .build();
        assertEquals("Bucket was not set", BUCKET, task.getBucketName());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_Batch() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME))
                .build();
        assertEquals("Batch was not set", BATCH, task.getBatch());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_Callback() {
        BulkDeleteTask task = builder.withDeleteRange(new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME))
                .build();
        assertEquals("Callback was not set", callback, task.getCallback());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_DeleteTileRangeSet() {
        DeleteTileLayer deleteTileRange = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        BulkDeleteTask task = builder.withDeleteRange(deleteTileRange).build();
        assertEquals("DeleteTileRange was not set", deleteTileRange, task.getDeleteTileRange());
    }
}
