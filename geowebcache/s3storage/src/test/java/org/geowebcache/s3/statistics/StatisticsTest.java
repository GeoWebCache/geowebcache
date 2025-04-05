package org.geowebcache.s3.statistics;


import org.junit.Test;

import static org.geowebcache.s3.statistics.StatisticsTestHelper.ALL_ONE_SUBSTATS;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.EMPTY_STATISTICS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StatisticsTest {

    ///////////////////////////////////////////////////////////////////////////
    // Add SubStats

    @Test
    public void testAddStat() {
        Statistics statistics = EMPTY_STATISTICS();
        SubStats subStats = ALL_ONE_SUBSTATS();

        statistics.addSubStats(subStats);

        assertThat("Expected there to be 1 subStat", 1, is(statistics.getSubStats().size()));
        assertThat("Expected deleted to be 1 ", 1L, is(statistics.getDeleted()));
        assertThat("Expected processed to be 1 ", 1L, is(statistics.getProcessed()));
        assertThat("Expected batchSent to be 1 ", 1L, is(statistics.getBatchSent()));
        assertThat("Expected batchTotal to be 1 ", 1L, is(statistics.getBatchTotal()));
        assertThat("Expected batchLowTideLevel to be 1 ", 1L, is(statistics.getBatchLowTideLevel()));
        assertThat("Expected batchHighTideLevel to be 1 ", 1L, is(statistics.getBatchHighTideLevel()));
        assertThat("Expected recoverable issues to be set", subStats.recoverableIssues, is(statistics.recoverableIssues));
        assertThat("Expected non recoverable issues to be set", subStats.nonRecoverableIssues, is(statistics.nonRecoverableIssues));
        assertThat("Expected unknown issues to be set", subStats.unknownIssues, is(statistics.unknownIssues));
        assertThat("Expected the substats to be saved", subStats, is(statistics.getSubStats().get(0)));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Recoverable issue tests

    @Test
    public void testAddRecoverableIssue() throws Exception {
        Statistics statistics = EMPTY_STATISTICS();
        RuntimeException issue = new RuntimeException();
        statistics.addRecoverableIssue(issue);

        assertThat("Expected there to be one issue", 1, is(statistics.getRecoverableIssuesSize()));
        assertThat("Expected the first issue to be present", statistics.getRecoverableIssues().findFirst().isPresent());
        assertThat("Expected the issue to be set", issue, is(statistics.getRecoverableIssues().findFirst().get()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // NonRecoverable issue tests

    @Test
    public void testAddNonRecoverableIssue() throws Exception {
        Statistics statistics = EMPTY_STATISTICS();
        RuntimeException issue = new RuntimeException();
        statistics.addNonRecoverableIssue(issue);

        assertThat("Expected there to be one issue", 1, is(statistics.getNonRecoverableIssuesSize()));
        assertThat("Expected the first issue to be present", statistics.getNonRecoverableIssues().findFirst().isPresent());
        assertThat("Expected the issue to be set", issue, is(statistics.getNonRecoverableIssues().findFirst().get()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Unknown issue tests

    @Test
    public void testAddUnknownIssue() throws Exception {
        Statistics statistics = EMPTY_STATISTICS();
        RuntimeException issue = new RuntimeException();
        statistics.addUnknownIssue(issue);

        assertThat("Expected there to be one issue", 1, is(statistics.getUnknownIssuesSize()));
        assertThat("Expected the first issue to be present", statistics.getUnknownIssues().findFirst().isPresent());
        assertThat("Expected the issue to be set", issue, is(statistics.getUnknownIssues().findFirst().get()));
    }
}