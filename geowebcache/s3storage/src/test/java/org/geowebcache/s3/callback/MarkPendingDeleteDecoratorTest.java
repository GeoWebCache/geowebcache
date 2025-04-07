package org.geowebcache.s3.callback;

import static org.geowebcache.s3.callback.CallbackTestHelper.WithSubTaskStarted;
import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.*;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Properties;
import java.util.logging.Logger;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.s3.S3BlobStore;
import org.geowebcache.s3.S3Ops;
import org.geowebcache.s3.delete.DeleteTileInfo;
import org.geowebcache.s3.delete.DeleteTileLayer;
import org.geowebcache.s3.delete.DeleteTileObject;
import org.geowebcache.s3.delete.DeleteTilePrefix;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;
import org.geowebcache.storage.StorageException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MarkPendingDeleteDecoratorTest {
    private MarkPendingDeleteDecorator markPendingDeleteDecorator;
    private CaptureCallback captureCallback;
    private Logger logger = S3BlobStore.getLog();

    @Mock
    private S3Ops s3Ops;

    @Mock
    Logger mockLogger;

    @Captor
    ArgumentCaptor<Properties> propertiesCaptor;

    @Before
    public void setUp() {
        captureCallback = new CaptureCallback();
        markPendingDeleteDecorator = new MarkPendingDeleteDecorator(captureCallback, s3Ops, logger);
    }

    ///////////////////////////////////////////////////////////////////////////
    // test constructor

    @Test
    public void test_constructor_delegateCannotBeNull() {
        assertThrows(
                "delegate cannot be null",
                NullPointerException.class,
                () -> markPendingDeleteDecorator = new MarkPendingDeleteDecorator(null, s3Ops, logger));
    }

    @Test
    public void test_constructor_s3OpsCannotBeNull() {
        assertThrows(
                "s3Ops cannot be null",
                NullPointerException.class,
                () -> markPendingDeleteDecorator = new MarkPendingDeleteDecorator(new NoopCallback(), null, logger));
    }

    @Test
    public void test_constructor_loggerCannotBeNull() {
        assertThrows(
                "logger cannot be null",
                NullPointerException.class,
                () -> markPendingDeleteDecorator = new MarkPendingDeleteDecorator(new NoopCallback(), s3Ops, null));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test taskStarted()

    @Test
    public void test_taskStarted_ensureDelegateIsCalled() {
        Statistics testStatistics = EMPTY_STATISTICS();
        markPendingDeleteDecorator.taskStarted(testStatistics);
        assertThat("Expected the delegate to have been called", captureCallback.getTaskStartedCount(), is(1L));
        assertThat("Expected the statistics to be passed through", captureCallback.statistics, is(testStatistics));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test taskEnded()

    @Test
    public void test_taskEnded_ensureDelegateIsCalled() {
        markPendingDeleteDecorator.taskEnded();

        assertThat("Expected the delegate to have been called", captureCallback.getTaskEndedCount(), is(1L));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test subTaskStarted()
    @Test
    public void test_subTaskStarted_insertPendingDeleted_withS3ObjectPathsForPrefix() throws StorageException {
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        SubStats subStats = new SubStats(deleteTileLayer, S3ObjectPathsForPrefix);

        when(s3Ops.getProperties(anyString())).thenReturn(new Properties());
        markPendingDeleteDecorator.subTaskStarted(subStats);

        verify(s3Ops, times(1)).getProperties(anyString());
        verify(s3Ops, times(1)).putProperties(anyString(), propertiesCaptor.capture());
        assertThat(
                "There should be a single property", propertiesCaptor.getValue().size(), is(1));
        verifyNoMoreInteractions(s3Ops);
    }

    @Test
    public void test_subTaskStarted_insertPendingDeleted_withSingleTile() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(TILE_OBJECT, PREFIX, false);
        SubStats subStats = new SubStats(deleteTileObject, SingleTile);

        markPendingDeleteDecorator.subTaskStarted(subStats);

        verifyNoInteractions(s3Ops);
    }

    @Test
    public void test_subTaskStarted_insertPendingDeleted_getProperties() throws StorageException {
        markPendingDeleteDecorator = new MarkPendingDeleteDecorator(captureCallback, s3Ops, logger);
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        String path = deleteTileLayer.path();
        SubStats subStats = new SubStats(deleteTileLayer, S3ObjectPathsForPrefix);

        when(s3Ops.getProperties(path)).thenThrow(new RuntimeException("GetProperties failed"));
        markPendingDeleteDecorator.subTaskStarted(subStats);

        verify(s3Ops, times(1)).getProperties(path);
        verifyNoMoreInteractions(s3Ops);
    }

    @Test
    public void test_subTaskStarted_insertPendingDeleted_withRetryPendingTask() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(TILE_OBJECT, PREFIX, false);
        SubStats subStats = new SubStats(deleteTileObject, RetryPendingTask);

        markPendingDeleteDecorator.subTaskStarted(subStats);

        verifyNoInteractions(s3Ops);
    }

    @Test
    public void test_subTaskStarted_ensureDelegateIsCalled() {
        when(s3Ops.getProperties(anyString())).thenReturn(new Properties());
        SubStats subStats = EMPTY_SUB_STATS();
        markPendingDeleteDecorator.subTaskStarted(subStats);

        assertThat("Expected the delegate to have been called", captureCallback.getSubTaskStartedCount(), is(1L));
        assertThat("Expected a single subStats", captureCallback.getSubStats().size(), is(1));
        captureCallback.getSubStats().stream()
                .findFirst()
                .ifPresentOrElse(
                        stats ->
                                assertThat("Expected the EMPTY_STATISTICS() to be passed through", subStats, is(stats)),
                        () -> fail("Missing expected subStat"));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test subTaskEnded()

    @Test
    public void test_subTaskEnded_removePendingDeleted_withDeleteTileLayer() throws Exception {
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        String path = deleteTileLayer.path();
        SubStats subStats = new SubStats(deleteTileLayer, S3ObjectPathsForPrefix);

        when(s3Ops.getProperties(path)).thenReturn(new Properties());
        markPendingDeleteDecorator.subTaskStarted(subStats);

        doNothing().when(s3Ops).clearPendingBulkDelete(anyString(), anyLong());
        markPendingDeleteDecorator.subTaskEnded();

        verify(s3Ops, times(1)).getProperties(path);
        verify(s3Ops, times(1)).putProperties(eq(path), propertiesCaptor.capture());
        verify(s3Ops, times(1)).clearPendingBulkDelete(eq(path), anyLong());
        verifyNoMoreInteractions(s3Ops);
    }

    @Test
    public void test_subTaskEnded_removePendingDeleted_withRetryPendingTask() throws Exception {
        String path =
                DeleteTileInfo.toZoomPrefix(PREFIX, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4);
        DeleteTilePrefix deleteTilePrefix = new DeleteTilePrefix(PREFIX, BUCKET, path);
        SubStats subStats = new SubStats(deleteTilePrefix, RetryPendingTask);

        markPendingDeleteDecorator.subTaskStarted(subStats);

        doNothing().when(s3Ops).clearPendingBulkDelete(anyString(), anyLong());
        markPendingDeleteDecorator.subTaskEnded();

        verify(s3Ops, times(1)).clearPendingBulkDelete(eq(path), anyLong());
        verifyNoMoreInteractions(s3Ops);
    }

    @Test
    public void test_subTaskEnded_removePendingDeleted_notWithSingleTileStrategy() throws StorageException {
        DeleteTileObject deleteTileObject = new DeleteTileObject(TILE_OBJECT, PREFIX, false);
        SubStats subStats = new SubStats(deleteTileObject, SingleTile);

        markPendingDeleteDecorator.subTaskStarted(subStats);

        verifyNoInteractions(s3Ops);
    }

    @Test
    public void test_subTaskEnded_removePendingDeleted_notWithNoDeletionsRequiredStrategy() throws StorageException {
        DeleteTileObject deleteTileObject = new DeleteTileObject(TILE_OBJECT, PREFIX, false);
        SubStats subStats = new SubStats(deleteTileObject, NoDeletionsRequired);

        markPendingDeleteDecorator.subTaskStarted(subStats);

        verifyNoInteractions(s3Ops);
    }

    @Test
    public void test_subTaskEnded_removePendingDeleted_clearPropertiesThrowsGeoWebCacheException() throws Exception {
        markPendingDeleteDecorator = new MarkPendingDeleteDecorator(captureCallback, s3Ops, mockLogger);
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        String path = deleteTileLayer.path();
        SubStats subStats = new SubStats(deleteTileLayer, S3ObjectPathsForPrefix);

        when(s3Ops.getProperties(path)).thenReturn(new Properties());
        doNothing().when(mockLogger).info(anyString());
        doNothing().when(mockLogger).warning(anyString());
        markPendingDeleteDecorator.subTaskStarted(subStats);

        doThrow(new GeoWebCacheException("Test exception")).when(s3Ops).clearPendingBulkDelete(anyString(), anyLong());
        markPendingDeleteDecorator.subTaskEnded();

        verify(s3Ops, times(1)).getProperties(path);
        verify(s3Ops, times(1)).putProperties(eq(path), propertiesCaptor.capture());
        verify(s3Ops, times(1)).clearPendingBulkDelete(eq(path), anyLong());
        verify(mockLogger).info(anyString());
        verify(mockLogger, times(1)).warning(anyString());
        verifyNoMoreInteractions(s3Ops, mockLogger);
    }

    @Test
    public void test_subTaskEnded_removePendingDeleted_clearPropertiesThrowsStorageException() throws Exception {
        markPendingDeleteDecorator = new MarkPendingDeleteDecorator(captureCallback, s3Ops, mockLogger);
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        String path = deleteTileLayer.path();
        SubStats subStats = new SubStats(deleteTileLayer, S3ObjectPathsForPrefix);

        when(s3Ops.getProperties(path)).thenReturn(new Properties());
        doNothing().when(mockLogger).info(anyString());
        doNothing().when(mockLogger).warning(anyString());
        markPendingDeleteDecorator.subTaskStarted(subStats);

        doThrow(new RuntimeException(new StorageException("Test exception")))
                .when(s3Ops)
                .clearPendingBulkDelete(anyString(), anyLong());
        markPendingDeleteDecorator.subTaskEnded();

        verify(s3Ops, times(1)).getProperties(path);
        verify(s3Ops, times(1)).putProperties(eq(path), propertiesCaptor.capture());
        verify(s3Ops, times(1)).clearPendingBulkDelete(eq(path), anyLong());
        verify(mockLogger).info(anyString());
        verify(mockLogger, times(1)).warning(anyString());
        verifyNoMoreInteractions(s3Ops, mockLogger);
    }

    @Test
    public void test_subTaskEnded_removePendingDeleted_clearPropertiesThrowsRuntimeException() throws Exception {
        markPendingDeleteDecorator = new MarkPendingDeleteDecorator(captureCallback, s3Ops, mockLogger);
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        String path = deleteTileLayer.path();
        SubStats subStats = new SubStats(deleteTileLayer, S3ObjectPathsForPrefix);

        when(s3Ops.getProperties(path)).thenReturn(new Properties());
        markPendingDeleteDecorator.subTaskStarted(subStats);

        doThrow(new RuntimeException("Test exception")).when(s3Ops).clearPendingBulkDelete(anyString(), anyLong());
        markPendingDeleteDecorator.subTaskEnded();

        verify(s3Ops, times(1)).getProperties(path);
        verify(s3Ops, times(1)).putProperties(eq(path), propertiesCaptor.capture());
        verify(s3Ops, times(1)).clearPendingBulkDelete(eq(path), anyLong());
        verify(mockLogger).info(anyString());
        verify(mockLogger, times(1)).severe(anyString());
        verifyNoMoreInteractions(s3Ops, mockLogger);
    }

    @Test
    public void test_subTaskEnded_ensureDelegateIsCalled() {
        WithSubTaskStarted(markPendingDeleteDecorator);
        markPendingDeleteDecorator.subTaskEnded();
        assertThat("Expected the delegate to have been called", captureCallback.getSubTaskEndedCount(), is(1L));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test batchStarted()

    @Test
    public void test_batchStarted_ensureDelegateIsCalled() {
        BatchStats batchStats = EMPTY_BATCH_STATS();

        markPendingDeleteDecorator.batchStarted(batchStats);
        assertThat("Expected the delegate to have been called", captureCallback.getBatchStartedCount(), is(1L));
        assertThat("Expected a single subStats", captureCallback.getBatchStats().size(), is(1));
        captureCallback.getBatchStats().stream()
                .findFirst()
                .ifPresentOrElse(
                        stats -> assertThat("Expected the statistics to be passed through", batchStats, is(stats)),
                        () -> fail("Missing expected batch stat"));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test batchEnded()
    @Test
    public void test_batchEnded_ensureDelegateIsCalled() {
        markPendingDeleteDecorator.batchEnded();
        assertThat("Expected the delegate to have been called", captureCallback.getBatchEndedCount(), is(1L));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test tileDeleted()

    @Test
    public void test_tileResult_ensureDelegateIsCalled() {
        markPendingDeleteDecorator.tileResult(EMPTY_RESULT_STAT());
        assertThat("Expected the delegate to have been called", captureCallback.getTileResultCount(), is(1L));
    }
}
