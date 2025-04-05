package org.geowebcache.s3.callback;

import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;

/**
 * Used to provide lifecycle functionality to tasks that are being processed
 *
 *
 */
public interface Callback {
    void tileResult(ResultStat result);

    void batchStarted(BatchStats batchStats);

    void batchEnded();

    void subTaskStarted(SubStats subStats);

    void subTaskEnded();

    void taskStarted(Statistics statistics);

    void taskEnded();
}