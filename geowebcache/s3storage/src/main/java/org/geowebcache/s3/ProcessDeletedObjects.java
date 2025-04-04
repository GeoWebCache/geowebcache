package org.geowebcache.s3;

import com.amazonaws.services.s3.model.DeleteObjectsResult;
import java.util.function.ToLongFunction;

public class ProcessDeletedObjects implements ToLongFunction<DeleteObjectsResult> {
    private BulkDeleteTask.Statistics.SubStats stats;

    public ProcessDeletedObjects(BulkDeleteTask.Statistics.SubStats stats) {
        this.stats = stats;
    }

    @Override
    public long applyAsLong(DeleteObjectsResult result) {
        int count = result.getDeletedObjects().size();
        stats.incrementDeleted(count);
        return (long) count;
    }
    ;
}
