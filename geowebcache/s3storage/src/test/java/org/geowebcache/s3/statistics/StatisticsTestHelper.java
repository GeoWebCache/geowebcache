package org.geowebcache.s3.statistics;

import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.DefaultStrategy;
import static org.geowebcache.s3.delete.DeleteTestHelper.DELETE_TILE_RANGE;
import static org.geowebcache.s3.statistics.ResultStat.Change.Deleted;

public class StatisticsTestHelper {
    public static Long FILE_SIZE = 1_000_000L;
    public static final String RESULT_PATH = "layer_id/grid_set/format/parametersID/z/x/y.extension";

    public static SubStats ALL_ONE_SUBSTATS() {
        SubStats subStats = new SubStats(DELETE_TILE_RANGE, DefaultStrategy);
        subStats.deleted = 1;
        subStats.processed = 1;
        subStats.count = 1;
        subStats.batchSent = 1;
        subStats.batchTotal = 1;
        subStats.batchLowTideLevel = 1;
        subStats.batchHighTideLevel = 1;

        RuntimeException issue = new RuntimeException();
        subStats.addRecoverableIssue(issue);
        subStats.addNonRecoverableIssue(issue);
        subStats.addUnknownIssue(issue);

        return subStats;
    }

    public static Statistics EMPTY_STATISTICS() {
        return new Statistics(DELETE_TILE_RANGE);
    }

    public static SubStats EMPTY_SUB_STATS() {
        return new SubStats(DELETE_TILE_RANGE, DefaultStrategy);
    }

    public static BatchStats EMPTY_BATCH_STATS() {
        return new BatchStats(DELETE_TILE_RANGE);
    }

    public static ResultStat EMPTY_RESULT_STAT() {
        return new ResultStat(DELETE_TILE_RANGE, RESULT_PATH, null, 0, 0, Deleted);
    }
}
