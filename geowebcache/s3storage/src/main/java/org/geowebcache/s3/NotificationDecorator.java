package org.geowebcache.s3;

import com.google.common.base.Preconditions;
import org.geowebcache.s3.BulkDeleteTask.BatchStats;
import org.geowebcache.s3.BulkDeleteTask.Callback;
import org.geowebcache.s3.BulkDeleteTask.ResultStat;
import org.geowebcache.s3.BulkDeleteTask.Statistics;
import org.geowebcache.s3.BulkDeleteTask.Statistics.SubStats;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public class NotificationDecorator implements Callback {

    private final Callback delegate;
    private final BlobStoreListenerList listeners;

    private SubStats currentSubStats;

    public NotificationDecorator(Callback delegate, BlobStoreListenerList listeners) {
        checkNotNull(delegate, "decorator cannot be null");
        this.delegate = delegate;
        this.listeners = listeners;
    }


    @Override
    public void tileDeleted(ResultStat statistics) {
        delegate.tileDeleted(statistics);
        notifyTileDeleted(statistics);
    }

    @Override
    public void batchStarted(BatchStats batchStats) {
        delegate.batchStarted(batchStats);
    }

    @Override
    public void batchEnded() {
        delegate.batchEnded();
    }

    @Override
    public void subTaskStarted(SubStats subStats) {
        this.currentSubStats = subStats;

        delegate.subTaskStarted(subStats);
    }

    @Override
    public void subTaskEnded() {
        delegate.subTaskEnded();


        if (listeners.isEmpty()) {
            return;
        }

        notifyWhenSubTaskEnded(currentSubStats);
    }

    void notifyWhenSubTaskEnded(SubStats subStats) {
        DeleteTileRange deleteTileRange = subStats.deleteTileRange;
        if (deleteTileRange instanceof DeleteTileLayer) {
            notifyLayerDeleted(subStats, (DeleteTileLayer) deleteTileRange);
        }

        if (deleteTileRange instanceof DeleteTileGridSet) {
            notifyGridSetDeleted(subStats, (DeleteTileGridSet) deleteTileRange);
        }

        if (deleteTileRange instanceof DeleteTileParameterId) {
            notifyWhenParameterId(subStats, (DeleteTileParameterId) deleteTileRange);
        }
    }

    @Override
    public void taskStarted(Statistics statistics) {
        delegate.taskStarted(statistics);
    }

    @Override
    public void taskEnded() {
        delegate.taskEnded();
    }


    // Single tile to delete
    void notifyTileDeleted(ResultStat statistic) {
        if (listeners.isEmpty()) {
            return;
        }

        if (statistic.tileObject != null) {
            listeners.sendTileDeleted(statistic.tileObject);
        } else {
            S3BlobStore.log.warning(format("No tile object found for %s cannot notify of deletion", statistic.path));
        }
    }

    void notifyGridSetDeleted(SubStats statistics, DeleteTileGridSet deleteTileRange) {
        if (statistics.completed()) {
            listeners.sendGridSubsetDeleted(deleteTileRange.getLayerName(), deleteTileRange.getGridSetId());
        }
    }

    void notifyLayerDeleted(SubStats statistics, DeleteTileLayer deleteLayer) {
        if (statistics.completed()) {
            for (BlobStoreListener listener : listeners.getListeners()) {
                listener.layerDeleted(deleteLayer.getLayerName());
            }
        }
    }

    void notifyWhenParameterId(SubStats statistics, DeleteTileParameterId deleteLayer) {
        if (statistics.completed()) {
            listeners.sendParametersDeleted(deleteLayer.getLayerName(), deleteLayer.getLayerName());
        }
    }
}

