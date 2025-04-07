package org.geowebcache.s3.callback;

import org.geowebcache.s3.delete.*;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;

import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public class NotificationDecorator implements Callback {

    private final Callback delegate;
    private final BlobStoreListenerList listeners;
    private final Logger logger;

    private SubStats currentSubStats;

    public NotificationDecorator(Callback delegate, BlobStoreListenerList listeners, Logger logger) {
        checkNotNull(delegate, "delegate cannot be null");
        checkNotNull(listeners, "listeners cannot be null");
        checkNotNull(logger, "logger cannot be null");

        this.delegate = delegate;
        this.listeners = listeners;
        this.logger = logger;
    }

    @Override
    public void tileResult(ResultStat statistics) {
        delegate.tileResult(statistics);
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
        checkNotNull(subStats, "subStats cannot be null, missing subTaskStart message");

        DeleteTileRange deleteTileRange = subStats.getDeleteTileRange();
        if (deleteTileRange instanceof DeleteTileLayer) {
            notifyLayerDeleted(subStats, (DeleteTileLayer) deleteTileRange);
        }

        if (deleteTileRange instanceof DeleteTileGridSet) {
            notifyGridSetDeleted(subStats, (DeleteTileGridSet) deleteTileRange);
        }

        if (deleteTileRange instanceof DeleteTileParametersId) {
            notifyWhenParameterId(subStats, (DeleteTileParametersId) deleteTileRange);
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
    void notifyTileDeleted(ResultStat stats) {
        if (listeners.isEmpty()) {
            return;
        }

        if (checkDeleteLayerCompatibleWithTileDeleted(stats)) return;

        if (stats.getTileObject() != null) {
            listeners.sendTileDeleted(stats.getTileObject());
        } else {
            logger
                    .warning(format("No tile object found for %s cannot notify of deletion", stats.getPath()));
        }
    }

    private static boolean checkDeleteLayerCompatibleWithTileDeleted(ResultStat stats) {
        return !(stats.getDeleteTileRange() instanceof DeleteTileObject
                || stats.getDeleteTileRange() instanceof DeleteTileZoom
                || stats.getDeleteTileRange() instanceof DeleteTileZoomInBoundedBox);
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

    void notifyWhenParameterId(SubStats statistics, DeleteTileParametersId deleteLayer) {
        if (statistics.completed()) {
            listeners.sendParametersDeleted(deleteLayer.getLayerName(), deleteLayer.getLayerName());
        }
    }
}
