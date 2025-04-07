package org.geowebcache.s3.callback;

import static org.geowebcache.s3.callback.CallbackTestHelper.WithSubTaskStarted;
import static org.geowebcache.s3.callback.LockProviderCapture.LockProviderMode.AlwaysSucceed;
import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.LOGGER;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import java.util.logging.Logger;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;
import org.junit.Before;
import org.junit.Test;

public class LockingDecoratorTest {
    private LockingDecorator lockingDecorator;
    private CaptureCallback captureCallback;
    private LockProviderCapture lockProvider;
    private final Logger logger = LOGGER;

    @Before
    public void setUp() {
        captureCallback = new CaptureCallback();
        lockProvider = new LockProviderCapture(AlwaysSucceed);
        lockingDecorator = new LockingDecorator(captureCallback, lockProvider, logger);
    }

    ///////////////////////////////////////////////////////////////////////////
    // test constructor

    @Test
    public void test_constructor_delegateCannotBeNull() {
        Exception exp = assertThrows(
                "delegate cannot be null",
                NullPointerException.class,
                () -> lockingDecorator = new LockingDecorator(null, lockProvider, logger));
        System.out.println(exp.getMessage());
    }

    @Test
    public void test_constructor_lockProvider_CannotBeNull() {
        assertThrows(
                "lockProvider cannot be null",
                NullPointerException.class,
                () -> lockingDecorator = new LockingDecorator(new NoopCallback(), null, logger));
    }

    @Test
    public void test_constructor_logger_CannotBeNull() {
        assertThrows(
                "BlobStoreListners cannot be null",
                NullPointerException.class,
                () -> lockingDecorator = new LockingDecorator(new NoopCallback(), lockProvider, null));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test taskStarted()

    @Test
    public void test_taskStarted_ensureDelegateIsCalled() {
        Statistics testStatistics = EMPTY_STATISTICS();
        lockingDecorator.taskStarted(testStatistics);
        assertThat("Expected the delegate to have been called", captureCallback.getTaskStartedCount(), is(1L));
        assertThat("Expected the statistics to be passed through", captureCallback.statistics, is(testStatistics));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test taskEnded()

    @Test
    public void test_taskEnded_ensureDelegateIsCalled() {
        lockingDecorator.taskEnded();

        assertThat("Expected the delegate to have been called", captureCallback.getTaskEndedCount(), is(1L));
    }

    @Test
    public void test_tileResult_willRemoveOutstandingLocks() {
        WithSubTaskStarted(lockingDecorator);

        lockingDecorator.taskEnded();
        assertThat("Expected the Locking provider lock to have been called", lockProvider.getLockCount(), is(1L));
        assertThat("Expected the Locking provider release to have been called", lockProvider.getUnlockCount(), is(1L));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test subTaskStarted()

    @Test
    public void test_subTaskStarted_ensureDelegateIsCalled() {
        SubStats subStats = EMPTY_SUB_STATS();
        lockingDecorator.subTaskStarted(subStats);

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
    public void test_subTaskEnded_ensureDelegateIsCalled() {
        WithSubTaskStarted(lockingDecorator);
        lockingDecorator.subTaskEnded();
        assertThat("Expected the delegate to have been called", captureCallback.getSubTaskEndedCount(), is(1L));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test batchStarted()

    @Test
    public void test_batchStarted_ensureDelegateIsCalled() {
        BatchStats batchStats = EMPTY_BATCH_STATS();

        lockingDecorator.batchStarted(batchStats);
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
        lockingDecorator.batchEnded();
        assertThat("Expected the delegate to have been called", captureCallback.getBatchEndedCount(), is(1L));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test tileDeleted()

    @Test
    public void test_tileResult_ensureDelegateIsCalled() {
        lockingDecorator.tileResult(EMPTY_RESULT_STAT());
        assertThat("Expected the delegate to have been called", captureCallback.getTileResultCount(), is(1L));
    }
}
