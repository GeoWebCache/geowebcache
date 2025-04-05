package org.geowebcache.s3.callback;

import org.geowebcache.s3.delete.BulkDeleteTask;
import org.geowebcache.s3.delete.BulkDeleteTask.Callback;

import static org.geowebcache.s3.statistics.StatisticsTestHelper.*;

public class CallbackTestHelper {
    static void WithTaskStarted(Callback callback) {
        callback.taskStarted(EMPTY_STATISTICS());
    }

    static void WithSubTaskStarted(Callback callback) {
        callback.taskStarted(EMPTY_STATISTICS());
        callback.subTaskStarted(EMPTY_SUB_STATS());
    }

    static void WithSubTaskEnded(Callback callback) {
        callback.taskStarted(EMPTY_STATISTICS());
        callback.subTaskStarted(EMPTY_SUB_STATS());
        callback.subTaskEnded();
    }

    static void WithBatchStarted(Callback callback) {
        callback.taskStarted(EMPTY_STATISTICS());
        callback.subTaskStarted(EMPTY_SUB_STATS());
        callback.batchStarted(EMPTY_BATCH_STATS());
    }
}
