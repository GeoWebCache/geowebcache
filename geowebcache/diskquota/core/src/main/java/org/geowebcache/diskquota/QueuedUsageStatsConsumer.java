/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.diskquota;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.diskquota.storage.PageStatsPayload;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.springframework.util.Assert;

/** @author groldan */
public class QueuedUsageStatsConsumer implements Callable<Long> {

    private static final Logger log = Logging.getLogger(QueuedUsageStatsConsumer.class.getName());

    private static final long serialVersionUID = -625181087112272266L;

    /** Default number of milliseconds before cached/aggregated quota update is saved to the store */
    private static final long DEFAULT_SYNC_TIMEOUT = 100;

    /**
     * Default number of per TileSet aggregated quota updates before ensuring they're synchronized back to the store,
     * regardless of whether the timeout expired for the TileSet
     */
    private static final int MAX_AGGREGATES_BEFORE_COMMIT = 3000;

    private final QuotaStore quotaStore;

    private final BlockingQueue<UsageStats> usageStatsQueue;

    private final TilePageCalculator tilePageCalculator;

    private final TimedUsageUpdate aggregatedPendingUpdates;

    /** @author groldan */
    private static class TimedUsageUpdate {
        /** Tracks aggregated usage stats per {@link TilePage#getId() pageId} until committed */
        private final Map<String, PageStatsPayload> pages;

        /** tracks the last time the aggregated updates for a given tile set were committed */
        private long lastCommitTime;

        /** tracks how many requests for the same tile page this aggregated stats is made of */
        private int numAggregations;

        public TimedUsageUpdate() {
            this.pages = new HashMap<>();
            this.lastCommitTime = System.currentTimeMillis();
            numAggregations = 0;
        }
    }

    /** */
    public QueuedUsageStatsConsumer(
            final QuotaStore quotaStore,
            final BlockingQueue<UsageStats> queue,
            final TilePageCalculator tilePageCalculator) {

        Assert.notNull(quotaStore, "quotaStore can't be null");
        Assert.notNull(queue, "queue can't be null");
        Assert.notNull(tilePageCalculator, "tilePageCalculator can't be null");

        this.quotaStore = quotaStore;
        this.usageStatsQueue = queue;
        this.tilePageCalculator = tilePageCalculator;
        aggregatedPendingUpdates = new TimedUsageUpdate();
    }

    /** @see java.util.concurrent.Callable#call() */
    @Override
    public Long call() {
        while (true) {
            if (Thread.interrupted()) {
                log.fine("Job " + getClass().getSimpleName() + " finished due to interrupted thread.");
                break;
            }

            if (terminate) {
                log.fine(
                        "Exiting on explicit termination request: " + getClass().getSimpleName());
                break;
            }

            try {
                /*
                 * do not wait for more than 5 seconds for data to become available on the queue
                 */
                UsageStats requestedTile = usageStatsQueue.poll(DEFAULT_SYNC_TIMEOUT, TimeUnit.MILLISECONDS);
                if (requestedTile == null) {
                    /*
                     * poll timed out, nothing new, check there are no pending aggregated updates
                     * for too long if we're idle
                     */
                    if (!aggregatedPendingUpdates.pages.isEmpty()) {
                        commit();
                    }

                } else {
                    /*
                     * perform an aggregated update in case we're really busy
                     */
                    performAggregatedUpdate(requestedTile);
                }
            } catch (InterruptedException e) {
                log.fine("Shutting down quota update background task due to interrupted exception");
                Thread.currentThread().interrupt();
                break;
                // it doesn't matter
            } catch (RuntimeException e) {
                // we're running as a single task on a single thread... we need to be really sure if
                // we should terminate... think how to handle recovery if at all
                log.log(Level.FINE, e.getMessage(), e);
                // throw e;
            }
        }

        return null;
    }

    private final int[] pageIndexTarget = new int[3];

    private final StringBuilder pageIdTarget = new StringBuilder(128);

    private boolean terminate = false;

    /**
     * @param requestedTile represents a single tile that was requested and for which its tile page needs to be looked
     *     up and updated
     */
    private void performAggregatedUpdate(final UsageStats requestedTile) throws InterruptedException {

        final TileSet tileSet = requestedTile.getTileSet();
        final String tileSetId = tileSet.getId();
        final long[] tileIndex = requestedTile.getTileIndex();

        tilePageCalculator.pageIndexForTile(tileSet, tileIndex, pageIndexTarget);
        final int pageX = pageIndexTarget[0];
        final int pageY = pageIndexTarget[1];
        final byte pageZ = (byte) pageIndexTarget[2];

        pageIdTarget.setLength(0);
        TilePage.computeId(tileSetId, pageX, pageY, pageZ, pageIdTarget);
        final String pageKeyForTile = pageIdTarget.toString();

        PageStatsPayload timedUpdate = aggregatedPendingUpdates.pages.get(pageKeyForTile);
        if (timedUpdate == null) {
            /*
             * it is the first one for this tile set, lets start the aggregated updates on it
             */
            timedUpdate = new PageStatsPayload(new TilePage(tileSetId, pageX, pageY, pageZ));
            timedUpdate.setTileSet(tileSet);

            aggregatedPendingUpdates.pages.put(pageKeyForTile, timedUpdate);
        } else {
            timedUpdate.setNumHits(timedUpdate.getNumHits() + 1);
        }
        timedUpdate.setNumHits(timedUpdate.getNumHits() + 1);
        timedUpdate.setLastAccessTime(System.currentTimeMillis());
        aggregatedPendingUpdates.numAggregations++;

        /*
         * now make sure we're not waiting for too long before committing
         */
        checkAggregatedTimeout();
    }

    /**
     * Makes sure the given cached updates are held for too long before synchronizing with the store, either because
     * it's been held for too long, or because too many updates have happened on it since the last time it was saved to
     * the store.
     */
    private void checkAggregatedTimeout() {

        final long currTime = System.currentTimeMillis();
        final long creationTime = aggregatedPendingUpdates.lastCommitTime;

        boolean timeout = currTime - creationTime >= DEFAULT_SYNC_TIMEOUT;

        final int numAggregations = aggregatedPendingUpdates.numAggregations;
        boolean tooManyPendingCommits = numAggregations >= MAX_AGGREGATES_BEFORE_COMMIT;

        if (timeout || tooManyPendingCommits) {
            if (log.isLoggable(Level.FINER)) {
                log.finer("Committing "
                        + numAggregations
                        + " aggregated usage stats to quota store due to "
                        + (tooManyPendingCommits ? "too many pending commits" : "max wait time reached"));
            }
            commit();
        }
    }

    private void commit() {
        Collection<PageStatsPayload> pendingCommits = new ArrayList<>(aggregatedPendingUpdates.pages.values());
        quotaStore.addHitsAndSetAccesTime(pendingCommits);
        aggregatedPendingUpdates.lastCommitTime = System.currentTimeMillis();
        aggregatedPendingUpdates.numAggregations = 0;
        aggregatedPendingUpdates.pages.clear();
    }

    public void shutdown() {
        this.terminate = true;
    }
}
