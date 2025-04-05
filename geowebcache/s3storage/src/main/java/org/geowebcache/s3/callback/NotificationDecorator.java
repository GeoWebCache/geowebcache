package org.geowebcache.s3.callback;

import org.geowebcache.s3.S3BlobStore;
import org.geowebcache.s3.delete.BulkDeleteTask.Callback;
import org.geowebcache.s3.delete.DeleteTileGridSet;
import org.geowebcache.s3.delete.DeleteTileLayer;
import org.geowebcache.s3.delete.DeleteTileParameterId;
import org.geowebcache.s3.delete.DeleteTileRange;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;
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
        DeleteTileRange deleteTileRange = subStats.getDeleteTileRange();
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

        if (statistic.getTileObject() != null) {
            listeners.sendTileDeleted(statistic.getTileObject());
        } else {
            S3BlobStore.getLog().warning(format("No tile object found for %s cannot notify of deletion", statistic.getPath()));
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

