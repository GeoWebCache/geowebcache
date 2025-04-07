package org.geowebcache.s3.callback;

import static org.geowebcache.s3.callback.CallbackTestHelper.WithBlobStoreListener;
import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.DefaultStrategy;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.geowebcache.s3.statistics.ResultStat.Change.Deleted;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.*;
import static org.geowebcache.s3.streams.StreamTestHelper.SINGLE_ZOOM_SINGLE_BOUND_MATCHING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import org.geowebcache.s3.delete.*;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.TileObject;
import org.junit.Before;
import org.junit.Test;

public class NotificationDecoratorTest {
    private CaptureCallback captureCallback;
    private NotificationDecorator notificationDecorator;
    private BlobStoreListenerList blobStoreListenerList;
    private BlobStoreCaptureListener captureListener;

    @Before
    public void setUp() {
        captureCallback = new CaptureCallback();
        captureListener = new BlobStoreCaptureListener();
        blobStoreListenerList = new BlobStoreListenerList();
        notificationDecorator = new NotificationDecorator(captureCallback, blobStoreListenerList);
    }

    ///////////////////////////////////////////////////////////////////////////
    // test constructor

    @Test
    public void test_constructor_delegateCannotBeNull() {
        Exception exp = assertThrows(
                "delegate cannot be null",
                NullPointerException.class,
                () -> notificationDecorator = new NotificationDecorator(null, blobStoreListenerList));
    }

    @Test
    public void test_constructor_blobStoreListenerCannotBeNull() {
        assertThrows(
                "BlobStoreListners cannot be null",
                NullPointerException.class,
                () -> notificationDecorator = new NotificationDecorator(new NoopCallback(), null));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test taskStarted()

    @Test
    public void test_taskStarted_ensureDelegateIsCalled() {
        Statistics testStatistics = EMPTY_STATISTICS();
        notificationDecorator.taskStarted(testStatistics);
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getTaskStartedCount()));
        assertThat("Expected the statistics to be passed through", testStatistics, is(captureCallback.statistics));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test taskEnded()

    @Test
    public void test_taskEnded_ensureDelegateIsCalled() {
        notificationDecorator.taskEnded();

        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getTaskEndedCount()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test subTaskStarted()

    @Test
    public void test_subTaskStarted_ensureDelegateIsCalled() {
        SubStats subStats = EMPTY_SUB_STATS();
        notificationDecorator.subTaskStarted(subStats);

        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getSubTaskStartedCount()));
        assertThat(
                "Expected a single subStats",
                1,
                is(captureCallback.getSubStats().size()));
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
    public void test_subTaskEnded_fromDeleteTileLayer_checkListenerIsCalled() {
        WithBlobStoreListener(blobStoreListenerList, captureListener);
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        SubStats subStats = new SubStats(deleteTileLayer, DefaultStrategy);
        notificationDecorator.subTaskStarted(subStats);
        notificationDecorator.subTaskEnded();
        assertThat("Expected the delegate to have been called", 1L, is(captureListener.layerDeletedCount));
    }

    @Test
    public void test_subTaskEnded_fromDeleteTileGridSet_checkListenerIsCalled() {
        WithBlobStoreListener(blobStoreListenerList, captureListener);
        DeleteTileGridSet deleteTileGridSet = new DeleteTileGridSet(PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, LAYER_NAME);
        SubStats subStats = new SubStats(deleteTileGridSet, DefaultStrategy);
        notificationDecorator.subTaskStarted(subStats);
        notificationDecorator.subTaskEnded();
        assertThat("Expected the delegate to have been called", 1L, is(captureListener.gridSetIdDeletedCount));
    }

    @Test
    public void test_subTaskEnded_fromDeleteTileParameters_checkListenerIsCalled() {
        WithBlobStoreListener(blobStoreListenerList, captureListener);
        DeleteTileParametersId deleteTileParametersId = new DeleteTileParametersId(
                PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, LAYER_NAME);
        SubStats subStats = new SubStats(deleteTileParametersId, DefaultStrategy);
        notificationDecorator.subTaskStarted(subStats);
        notificationDecorator.subTaskEnded();
        assertThat("Expected the delegate to have been called", 1L, is(captureListener.parametersDeletedCount));
    }

    @Test
    public void test_subTaskEnded_ensureDelegateIsCalled() {
        notificationDecorator.subTaskEnded();
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getSubTaskEndedCount()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test batchStarted()

    @Test
    public void test_batchStarted_ensureDelegateIsCalled() {
        BatchStats batchStats = EMPTY_BATCH_STATS();

        notificationDecorator.batchStarted(batchStats);
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getBatchStartedCount()));
        assertThat(
                "Expected a single subStats",
                1,
                is(captureCallback.getBatchStats().size()));
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
        notificationDecorator.batchEnded();
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getBatchEndedCount()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test tileDeleted()

    @Test
    public void test_tileDeleted_fromDeleteTileObject_checkListenerIsCalled() {
        WithBlobStoreListener(blobStoreListenerList, captureListener);
        TileObject tileObject =
                TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, null);
        tileObject.setBlobSize((int) FILE_SIZE);
        tileObject.setParametersId(PARAMETERS_ID);
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, RESULT_PATH, false);
        ResultStat resultStat =
                new ResultStat(deleteTileObject, RESULT_PATH, tileObject, FILE_SIZE, TIMESTAMP, Deleted);

        notificationDecorator.tileResult(resultStat);
        assertThat(
                "Expected the capture listener to be have its tileDeleted methods called once",
                1L,
                is(captureListener.tileDeletedCount));
    }

    @Test
    public void test_tileDeleted_fromDeleteTileLayer_checkListenerIsNotCalled() {
        WithBlobStoreListener(blobStoreListenerList, captureListener);
        TileObject tileObject =
                TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, null);
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        ResultStat resultStat = new ResultStat(deleteTileLayer, RESULT_PATH, tileObject, FILE_SIZE, TIMESTAMP, Deleted);

        notificationDecorator.tileResult(resultStat);
        assertThat("Expected the capture listener not to be called", 0L, is(captureListener.tileDeletedCount));
    }

    @Test
    public void test_tileResult_fromDeleteTilesZoom_checkListenerIsCalled() {
        WithBlobStoreListener(blobStoreListenerList, captureListener);
        TileObject tileObject =
                TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, null);
        tileObject.setBlobSize((int) FILE_SIZE);
        tileObject.setParametersId(PARAMETERS_ID);
        DeleteTileZoom deleteTileZoom =
                new DeleteTileZoom(PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, 10, SINGLE_ZOOM_SINGLE_BOUND_MATCHING);
        ResultStat resultStat = new ResultStat(deleteTileZoom, RESULT_PATH, tileObject, FILE_SIZE, TIMESTAMP, Deleted);

        notificationDecorator.tileResult(resultStat);
        assertThat(
                "Expected the capture listener to be have its tileDeleted methods called once",
                1L,
                is(captureListener.tileDeletedCount));
    }

    @Test
    public void test_tileResult_fromDeleteTilesZoomBounded_checkListenerIsCalled() {
        WithBlobStoreListener(blobStoreListenerList, captureListener);
        TileObject tileObject =
                TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, null);
        tileObject.setBlobSize((int) FILE_SIZE);
        tileObject.setParametersId(PARAMETERS_ID);
        DeleteTileZoom deleteTileZoom =
                new DeleteTileZoom(PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, 10, SINGLE_ZOOM_SINGLE_BOUND_MATCHING);
        ResultStat resultStat = new ResultStat(deleteTileZoom, RESULT_PATH, tileObject, FILE_SIZE, TIMESTAMP, Deleted);

        notificationDecorator.tileResult(resultStat);
        assertThat(
                "Expected the capture listener to be have its tileDeleted methods called once",
                1L,
                is(captureListener.tileDeletedCount));
    }

    @Test
    public void test_tileDeleted_fromDeleteTileObject_noListeners() {
        TileObject tileObject =
                TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, null);
        tileObject.setBlobSize((int) FILE_SIZE);
        tileObject.setParametersId(PARAMETERS_ID);
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, RESULT_PATH, false);
        ResultStat resultStat =
                new ResultStat(deleteTileObject, RESULT_PATH, tileObject, FILE_SIZE, TIMESTAMP, Deleted);

        // Just check no exceptions are raised
        notificationDecorator.tileResult(resultStat);
    }

    @Test
    public void test_tileResult_ensureDelegateIsCalled() {
        notificationDecorator.tileResult(EMPTY_RESULT_STAT());
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getTileResultCount()));
    }
}
