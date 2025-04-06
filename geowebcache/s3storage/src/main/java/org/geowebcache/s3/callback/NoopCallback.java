package org.geowebcache.s3.callback;

import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;

public class NoopCallback implements Callback {
    @Override
    public void tileResult(ResultStat result) {}

    @Override
    public void batchStarted(BatchStats batchStats) {}

    @Override
    public void batchEnded() {}

    @Override
    public void subTaskStarted(SubStats subStats) {}

    @Override
    public void subTaskEnded() {}

    @Override
    public void taskStarted(Statistics statistics) {}

    @Override
    public void taskEnded() {}
}
