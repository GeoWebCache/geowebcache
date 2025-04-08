package org.geowebcache.s3.callback;

import static org.geowebcache.s3.callback.CallbackTestHelper.WithBatchStarted;
import static org.geowebcache.s3.callback.CallbackTestHelper.WithSubTaskEnded;
import static org.geowebcache.s3.callback.CallbackTestHelper.WithSubTaskStarted;
import static org.geowebcache.s3.callback.CallbackTestHelper.WithTaskStarted;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.EMPTY_BATCH_STATS;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.EMPTY_RESULT_STAT;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.EMPTY_STATISTICS;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.EMPTY_SUB_STATS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.logging.Logger;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StatisticsCallbackDecoratorTest {

    @Mock
    Logger logger;

    private CaptureCallback captureCallback;
    private StatisticCallbackDecorator statisticCallbackDecorator;

    @Before
    public void setUp() {
        captureCallback = new CaptureCallback();
        statisticCallbackDecorator = new StatisticCallbackDecorator(logger, captureCallback);
    }

    ///////////////////////////////////////////////////////////////////////////
    // test taskStarted()

    @Test
    public void test_taskStarted_checkSetupOnFirstCall() {
        Statistics testStatistics = EMPTY_STATISTICS();
        statisticCallbackDecorator.taskStarted(testStatistics);
        assertThat("Expected statistics to be set", testStatistics, is(statisticCallbackDecorator.statistics));
    }

    @Test
    public void test_taskStarted_cannotCallTwice() {
        Statistics testStatistics = EMPTY_STATISTICS();
        statisticCallbackDecorator.taskStarted(testStatistics);
        assertThrows(
                "Cannot call taskStart twice in succession expected IllegalStateException",
                IllegalStateException.class,
                () -> statisticCallbackDecorator.taskStarted(testStatistics));
    }

    @Test
    public void test_taskStarted_ensureDelegateIsCalled() {
        Statistics testStatistics = EMPTY_STATISTICS();
        statisticCallbackDecorator.taskStarted(testStatistics);
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getTaskStartedCount()));
        assertThat("Expected the statistics to be passed through", testStatistics, is(captureCallback.statistics));
    }

    @Test
    public void test_taskStarted_statisticsCannotBeNull() {
        assertThrows(
                "statistics cannot be null when calling taskStarted",
                NullPointerException.class,
                () -> statisticCallbackDecorator.taskStarted(null));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test taskEnded()

    @Test
    public void test_taskEnded_cannotCallBeforeTaskStarted() {
        assertThrows(
                "Cannot call taskEnded before it is started expected IllegalStateException",
                IllegalStateException.class,
                () -> statisticCallbackDecorator.taskEnded());
    }

    @Test
    public void test_taskEnded_expectLogging_infoCalledOnce_tasksCompleted() {
        WithTaskStarted(statisticCallbackDecorator);

        statisticCallbackDecorator.taskEnded();

        verify(logger, atMostOnce()).info(anyString());
        Mockito.verifyNoMoreInteractions(logger);
    }

    @Test
    public void test_taskEnded_expectLogging_warningCalledOnce_tasksNotCompleted() {
        WithTaskStarted(statisticCallbackDecorator);
        statisticCallbackDecorator.statistics.addRecoverableIssue(new RuntimeException());

        statisticCallbackDecorator.taskEnded();
        verify(logger, atMostOnce()).warning(anyString());
        Mockito.verifyNoMoreInteractions(logger);
    }

    @Test
    public void test_taskEnded_expectLogging_infoCalled_forSubStats_tasksCompleted() {
        WithSubTaskEnded(statisticCallbackDecorator);

        statisticCallbackDecorator.taskEnded();

        // Logger called once for statistics and once for each subtask
        verify(logger, times(2)).info(anyString());
        Mockito.verifyNoMoreInteractions(logger);
    }

    @Test
    public void test_taskEnded_expectLogging_warningCalled_AndinfoCalled_forSubStats_tasksCompleted() {
        WithSubTaskStarted(statisticCallbackDecorator);
        statisticCallbackDecorator.statistics.addRecoverableIssue(new RuntimeException());
        statisticCallbackDecorator.subTaskEnded();
        statisticCallbackDecorator.taskEnded();

        // Logger called once at warning for statistics and once for each subtask
        verify(logger, times(1)).warning(anyString());
        verify(logger, times(1)).info(anyString());

        Mockito.verifyNoMoreInteractions(logger);
    }

    @Test
    public void test_taskEnded_ensureDelegateIsCalled() {
        WithTaskStarted(statisticCallbackDecorator);

        statisticCallbackDecorator.taskEnded();

        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getTaskEndedCount()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test subTaskStarted()

    @Test
    public void test_subTaskStarted_currentSubIsSet() {
        WithTaskStarted(statisticCallbackDecorator);

        SubStats subStats = EMPTY_SUB_STATS();
        statisticCallbackDecorator.subTaskStarted(subStats);

        assertThat("Expected the sub stats to be set", subStats, is(statisticCallbackDecorator.currentSub));
    }

    @Test
    public void test_subTaskStarted_subStatsCannotBeNull() {
        WithSubTaskStarted(statisticCallbackDecorator);
        assertThrows(
                "subStats cannot be null when calling subTaskStarted",
                NullPointerException.class,
                () -> statisticCallbackDecorator.subTaskStarted(null));
    }

    @Test
    public void test_subTaskStarted_cannotCallTwice() {
        WithSubTaskStarted(statisticCallbackDecorator);

        assertThrows(
                "Cannot call subTaskStart twice in succession expected IllegalStateException",
                IllegalStateException.class,
                () -> statisticCallbackDecorator.subTaskStarted(EMPTY_SUB_STATS()));
    }

    @Test
    public void test_subTaskStarted_ensureDelegateIsCalled() {
        WithTaskStarted(statisticCallbackDecorator);
        SubStats subStats = EMPTY_SUB_STATS();
        statisticCallbackDecorator.subTaskStarted(subStats);

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
    public void test_subTaskEnded_mustBeCalledAsfterSubTaskStarted() {
        Statistics statistics = EMPTY_STATISTICS();
        statisticCallbackDecorator.taskStarted(statistics);
        assertThrows(
                "subTaskStarted must be called before subTaskEnded",
                IllegalStateException.class,
                () -> statisticCallbackDecorator.subTaskEnded());
    }

    @Test
    public void test_subTaskEnded_mustBeCalledAfterTaskStarted() {
        WithTaskStarted(statisticCallbackDecorator);

        assertThrows(
                "taskStarted must be called before subTaskEnded",
                IllegalStateException.class,
                () -> statisticCallbackDecorator.subTaskEnded());
    }

    @Test
    public void test_subTaskEnded_currentSubIsAdded() {
        SubStats subStats = EMPTY_SUB_STATS();
        Statistics statistics = EMPTY_STATISTICS();
        statisticCallbackDecorator.taskStarted(statistics);
        statisticCallbackDecorator.subTaskStarted(subStats);
        statisticCallbackDecorator.subTaskEnded();

        assertThat(
                "Sub should have been added to statistics",
                subStats,
                is(statistics.getSubStats().get(0)));
    }

    @Test
    public void test_subTaskEnded_ensureDelegateIsCalled() {
        SubStats subStats = EMPTY_SUB_STATS();

        statisticCallbackDecorator.taskStarted(EMPTY_STATISTICS());
        statisticCallbackDecorator.subTaskStarted(subStats);
        statisticCallbackDecorator.subTaskEnded();
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getSubTaskEndedCount()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test batchStarted()

    @Test
    public void test_batchStarted_ensureDelegateIsCalled() {
        WithSubTaskStarted(statisticCallbackDecorator);
        BatchStats batchStats = EMPTY_BATCH_STATS();

        statisticCallbackDecorator.batchStarted(batchStats);
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getBatchStartedCount()));
        assertThat(
                "Expected a single subStats",
                1,
                is(captureCallback.getBatchStats().size()));
        captureCallback.getBatchStats().stream()
                .findFirst()
                .ifPresentOrElse(
                        stats -> assertThat(
                                "Expected the EMPTY_STATISTICS() to be passed through", batchStats, is(stats)),
                        () -> fail("Missing expected batch stat"));
    }

    ///////////////////////////////////////////////////////////////////////////
    // test batchEnded()
    @Test
    public void test_batchEnded_ensureDelegateIsCalled() {
        WithBatchStarted(statisticCallbackDecorator);
        statisticCallbackDecorator.batchEnded();
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getBatchEndedCount()));
    }

    @Test
    public void test_batchEnded_mustBeCalledAfterBatchStarted() {
        assertThrows(
                "batchStarted must be called before batchEnded",
                IllegalStateException.class,
                () -> statisticCallbackDecorator.batchEnded());
    }

    ///////////////////////////////////////////////////////////////////////////
    // test tileDeleted()

    @Test
    public void test_tileResult_ensureDelegateIsCalled() {
        WithBatchStarted(statisticCallbackDecorator);

        ResultStat resultStat = EMPTY_RESULT_STAT();
        statisticCallbackDecorator.tileResult(resultStat);
        assertThat("Expected the delegate to have been called", 1L, is(captureCallback.getTileResultCount()));
    }

    @Test
    public void test_tileResult_mustBeCalledAfterBatchStarted() {
        ResultStat resultStat = EMPTY_RESULT_STAT();

        assertThrows(
                "batchStarted must be called before tileDeleted",
                IllegalStateException.class,
                () -> statisticCallbackDecorator.tileResult(resultStat));
    }
}
