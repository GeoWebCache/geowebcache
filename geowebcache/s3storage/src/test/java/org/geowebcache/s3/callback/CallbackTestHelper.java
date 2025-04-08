package org.geowebcache.s3.callback;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.EMPTY_BATCH_STATS;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.EMPTY_STATISTICS;
import static org.geowebcache.s3.statistics.StatisticsTestHelper.EMPTY_SUB_STATS;

import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;

public class CallbackTestHelper {

    static void WithBlobStoreListener(BlobStoreListenerList blobStoreListenerList, BlobStoreListener captureListener) {
        checkNotNull(blobStoreListenerList);
        checkNotNull(captureListener);

        blobStoreListenerList.addListener(captureListener);
    }

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
