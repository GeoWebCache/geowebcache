package org.geowebcache.s3.statistics;

import static org.geowebcache.s3.statistics.StatisticsTestHelper.EMPTY_SUB_STATS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import junit.framework.TestCase;
import org.junit.Test;

public class SubStatsTest extends TestCase {
    ///////////////////////////////////////////////////////////////////////////
    // Recoverable issue tests

    @Test
    public void testAddRecoverableIssue() throws Exception {
        SubStats subStats = EMPTY_SUB_STATS();
        RuntimeException issue = new RuntimeException();
        subStats.addRecoverableIssue(issue);

        assertThat("Expected there to be one issue", 1, is(subStats.getRecoverableIssuesSize()));
        assertThat(
                "Expected the first issue to be present",
                subStats.getRecoverableIssues().findFirst().isPresent());
        assertThat(
                "Expected the issue to be set",
                issue,
                is(subStats.getRecoverableIssues().findFirst().get()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // NonRecoverable issue tests

    @Test
    public void testAddNonRecoverableIssue() throws Exception {
        SubStats subStats = EMPTY_SUB_STATS();
        RuntimeException issue = new RuntimeException();
        subStats.addNonRecoverableIssue(issue);

        assertThat("Expected there to be one issue", 1, is(subStats.getNonRecoverableIssuesSize()));
        assertThat(
                "Expected the first issue to be present",
                subStats.getNonRecoverableIssues().findFirst().isPresent());
        assertThat(
                "Expected the issue to be set",
                issue,
                is(subStats.getNonRecoverableIssues().findFirst().get()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Unknown issue tests

    @Test
    public void testAddUnknownIssue() throws Exception {
        SubStats subStats = EMPTY_SUB_STATS();
        RuntimeException issue = new RuntimeException();
        subStats.addUnknownIssue(issue);

        assertThat("Expected there to be one issue", 1, is(subStats.getUnknownIssuesSize()));
        assertThat(
                "Expected the first issue to be present",
                subStats.getUnknownIssues().findFirst().isPresent());
        assertThat(
                "Expected the issue to be set",
                issue,
                is(subStats.getUnknownIssues().findFirst().get()));
    }
}
