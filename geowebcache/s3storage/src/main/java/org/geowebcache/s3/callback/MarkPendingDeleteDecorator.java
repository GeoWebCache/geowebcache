package org.geowebcache.s3.callback;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.s3.S3Ops;
import org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy;
import org.geowebcache.s3.delete.DeleteTileRange;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;
import org.geowebcache.storage.StorageException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.geowebcache.s3.delete.BulkDeleteTask.ObjectPathStrategy.*;

public class MarkPendingDeleteDecorator implements Callback {
    private final Callback delegate;
    private final S3Ops s3Opts;
    private final Logger logger;

    private SubStats currentSubStats = null;
    private final Long pendingDeletesKeyTime;

    public MarkPendingDeleteDecorator(Callback delegate, S3Ops s3Opts, Logger logger) {
        checkNotNull(delegate, "delegate cannot be null");
        checkNotNull(s3Opts, "s3Opts cannot be null");
        checkNotNull(logger, "logger cannot be null");

        this.delegate = delegate;
        this.pendingDeletesKeyTime = Instant.now().minus(1, ChronoUnit.MINUTES).getEpochSecond();
        this.s3Opts = s3Opts;
        this.logger = logger;
    }

    @Override
    public void tileResult(ResultStat result) {
        delegate.tileResult(result);
    }

    @Override
    public void batchStarted(BatchStats stats) {
        delegate.batchStarted(stats);
    }

    @Override
    public void batchEnded() {
        delegate.batchEnded();
    }

    @Override
    public void subTaskStarted(SubStats subStats) {
        this.currentSubStats = subStats;

        if (shouldInsertAPendingDelete(subStats.getStrategy())) {
            String pendingDeletesKey = currentSubStats.getDeleteTileRange().path();
            insertPendingDelete(pendingDeletesKey);
        }

        delegate.subTaskStarted(subStats);
    }

    @Override
    public void subTaskEnded() {
        String pendingDeletesKey = currentSubStats.getDeleteTileRange().path();
        removeAnyPendingDelete(pendingDeletesKey);
        delegate.subTaskEnded();
    }

    @Override
    public void taskStarted(Statistics statistics) {
        delegate.taskStarted(statistics);
    }

    @Override
    public void taskEnded() {
        delegate.taskEnded();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Helper methods

    private static final List<ObjectPathStrategy> strategiesThatDoNotRequireAnInsert =
            List.of(NoDeletionsRequired, SingleTile, RetryPendingTask);

    /*
     * Only long running strategies should insert a marker for a running
     * pending delete. Also a RetryPendingDelete will already has a pending delete mark
     * inserted so it should be re-inserted
     * @return true when a Pending delete should be inserted
     */
    private boolean shouldInsertAPendingDelete(ObjectPathStrategy strategy) {
        checkNotNull(strategy, "strategy cannot be null");

        return !strategiesThatDoNotRequireAnInsert.contains(strategy);
    }

    private static final List<ObjectPathStrategy> strategiesThatDoNotRequireARemoval =
            List.of(NoDeletionsRequired, SingleTile);

    /*
     * Only short running strategies should not remove a marker for a running
     * pending delete.
     * @return true when a pending delete should be removed
     */
    private boolean shouldRemoveAPendingDelete(ObjectPathStrategy strategy) {
        return !strategiesThatDoNotRequireAnInsert.contains(strategiesThatDoNotRequireARemoval);
    }

    /*
     *  The behaviour appears a bit vague when dealing with errors.
     *  Currently do nothing just log out the fact that the removal of the pending delete has failed
     */
    private void removeAnyPendingDelete(String pendingDeletesKey) {
        try {
            s3Opts.clearPendingBulkDelete(pendingDeletesKey, pendingDeletesKeyTime);
        } catch (GeoWebCacheException | RuntimeException e) {

            if (e instanceof RuntimeException) {
                if (Objects.nonNull(e.getCause()) && e.getCause() instanceof StorageException) {
                    logger.warning(format(
                            "Unable to remove pending delete: %s issue with S3 storage, this will allow repeat calls to delete",
                            e.getCause().getMessage()));
                } else {
                    logger.severe(format(
                            "Unable to remove pending delete: %s unexpected runtime exception report to admin, this will allow repeat calls to delete",
                            e.getMessage()));
                }
            } else {
                logger.warning(format(
                        "Unable to remove pending delete: %s unexpected GeoWebException, this will allow repeat calls to delete",
                        e.getMessage()));
            }
        }
    }

    private void insertPendingDelete(String pendingDeletesKey) {
        try {
            DeleteTileRange deleteTileRange = currentSubStats.getDeleteTileRange();
            Properties deletes = s3Opts.getProperties(pendingDeletesKey);
            deletes.setProperty(deleteTileRange.path(), String.valueOf(pendingDeletesKeyTime));
            s3Opts.putProperties(pendingDeletesKey, deletes);
            logger.info(format("Inserted pending delete %s to persistent store ", pendingDeletesKey));
        } catch (RuntimeException | StorageException e) {
            logger.warning(format(
                    "Unable to mark pending deletes %s. Will continue with delete but persistant retry is not enabled.",
                    e.getMessage()));
        }
    }
}
