package org.geowebcache.s3;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.geowebcache.s3.BulkDeleteTaskTestHelper.*;
import static org.geowebcache.s3.BulkDeleteTaskTestHelper.LAYER_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class BulkDeleteTaskTest {
    @Mock
    public S3ObjectsWrapper s3ObjectsWrapper;

    @Mock
    public AmazonS3Wrapper amazonS3Wrapper;

    private BulkDeleteTask.Builder builder;
    private final CaptureCallback callback = new CaptureCallback(new BulkDeleteTask.LoggingCallback());

    @Before
    public void setup(){
        builder = BulkDeleteTask.newBuilder()
                .withAmazonS3Wrapper(amazonS3Wrapper)
                .withS3ObjectsWrapper(s3ObjectsWrapper)
                .withBucket(BUCKET)
                .withBatch(BATCH)
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


}
