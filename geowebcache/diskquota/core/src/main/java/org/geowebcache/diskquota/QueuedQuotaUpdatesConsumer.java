package org.geowebcache.diskquota;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.diskquota.storage.PageStatsPayload;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.springframework.util.Assert;

public class QueuedQuotaUpdatesConsumer implements Callable<Long>, Serializable {

    private static final Log log = LogFactory.getLog(QueuedQuotaUpdatesConsumer.class);

    private static final long serialVersionUID = -625181087112272266L;

    /**
     * Default number of milliseconds before cached/aggregated quota update is saved to the store
     */
    private static final long DEFAULT_SYNC_TIMEOUT = 100;

    /**
     * Default number of per TileSet aggregated quota updates before ensuring they're synchronized
     * back to the store, regardless of whether the timeout expired for the TileSet
     */
    private static final int MAX_AGGREGATES_BEFORE_COMMIT = 1000;

    private final QuotaStore quotaStore;

    private final TilePageCalculator tilePageCalculator;

    private final BlockingQueue<QuotaUpdate> queue;

    /**
     * Tracks aggregated quota size diffs per TileSet until committed by
     * {@link #commit(TimedQuotaUpdate)} as the result of {@link #checkAggregatedTimeouts()} or
     * {@link #checkAggregatedTimeout(TimedQuotaUpdate)} at {@link #call()}
     */
    private Map<TileSet, TimedQuotaUpdate> aggregatedDelayedUpdates;
    
    boolean terminate = false;

    /**
     * Tracks accumulated quota difference for a single TileSet and accumulated number of tiles
     * difference for pages in the same TileSet
     * 
     * @author groldan
     * 
     */
    private static class TimedQuotaUpdate {

        private final TilePageCalculator tpc;

        private final TileSet tileSet;

        /**
         * tracks the last time the aggregated updates for a given tile set were committed
         */
        private final long creationTime;

        /**
         * tracks how many quota updates this aggregated value is made of
         */
        private int numAggregations;

        /**
         * Tracks accumulated quota difference per TileSet
         */
        private Quota accumQuotaDiff;

        /**
         * Tracks accumulated number of tiles per TilePage id
         */
        private Map<String, PageStatsPayload> tilePages;

        private StringBuilder pageIdTarget;

        private int[] pageIndexTarget;

        public TimedQuotaUpdate(TileSet tileSet, TilePageCalculator tpc) {
            this.tileSet = tileSet;
            this.tpc = tpc;
            this.creationTime = System.currentTimeMillis();
            tilePages = new HashMap<String, PageStatsPayload>();
            pageIndexTarget = new int[3];
            pageIdTarget = new StringBuilder(128);
            accumQuotaDiff = new Quota();
        }

        public void add(QuotaUpdate quotaUpdate) {
            final String tileSetId = tileSet.getId();

            long size = quotaUpdate.getSize();
            this.accumQuotaDiff.addBytes(quotaUpdate.getSize());

            long[] tileIndex = quotaUpdate.getTileIndex();
            tpc.pageIndexForTile(tileSet, tileIndex, pageIndexTarget);
            int pageX = pageIndexTarget[0];
            int pageY = pageIndexTarget[1];
            byte pageZ = (byte) pageIndexTarget[2];
            pageIdTarget.setLength(0);
            TilePage.computeId(tileSetId, pageX, pageY, pageZ, pageIdTarget);
            String pageIdForTile = pageIdTarget.toString();

            final int tileCountDiff = size > 0 ? 1 : -1;
            PageStatsPayload payload = tilePages.get(pageIdForTile);
            if (payload == null) {
                TilePage page;
                page = new TilePage(tileSetId, pageX, pageY, pageZ);
                payload = new PageStatsPayload(page);
                tilePages.put(pageIdForTile, payload);
            }
            int previousCount = payload.getNumTiles();
            payload.setNumTiles(previousCount + tileCountDiff);

            ++numAggregations;
        }

        public TileSet getTileSet() {
            return tileSet;
        }

        public Quota getAccummulatedQuotaDifference() {
            return accumQuotaDiff;
        }

        public Collection<PageStatsPayload> getAccummulatedTilePageCounts() {
            return tilePages.values();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder('[');
            sb.append(tileSet);
            sb.append(numAggregations).append(" aggregated updates, ");
            sb.append(tilePages.size()).append(" different pages, ");
            sb.append("accum quota diff: ").append(accumQuotaDiff.toNiceString());
            sb.append(", created ").append((System.currentTimeMillis() - creationTime))
                    .append("ms ago").append(']');
            return sb.toString();
        }
    }

    public QueuedQuotaUpdatesConsumer(QuotaStore quotaStore, BlockingQueue<QuotaUpdate> queue) {
        Assert.notNull(quotaStore, "quotaStore can't be null");
        Assert.notNull(queue, "queue can't be null");

        this.quotaStore = quotaStore;
        this.tilePageCalculator = quotaStore.getTilePageCalculator();
        this.queue = queue;
        aggregatedDelayedUpdates = new HashMap<TileSet, TimedQuotaUpdate>();
    }

    /**
     * @see java.util.concurrent.Callable#call()
     */
    public Long call() {
        while (true) {
            if (Thread.interrupted()) {
                log.debug("Job " + getClass().getSimpleName()
                        + " finished due to interrupted thread.");
                break;
            }
            
            if(terminate) {
                log.debug("Exiting on explicit termination request: " + getClass().getSimpleName());
                break;
            }

            try {
                /*
                 * do not wait for more than DEFAULT_SYNC_TIMEOUT for data to become available on
                 * the queue
                 */
                QuotaUpdate updateData;
                updateData = queue.poll(DEFAULT_SYNC_TIMEOUT, TimeUnit.MILLISECONDS);
                if (updateData != null) {
                    /*
                     * or perform an aggregated update in case we're really busy
                     */
                    performAggregatedUpdate(updateData);
                }
                /*
                 * and check there are no pending aggregated updates for too long if we're idle
                 */
                checkAggregatedTimeouts();
            } catch (InterruptedException e) {
                log.info("Shutting down quota update background task due to InterruptedException");
                break;
                // it doesn't matter
            } catch (RuntimeException e) {
                // we're running as a single task on a single thread... we need to be really sure if
                // we should terminate... think how to handle recovery if at all
                log.debug(e);
                // throw e;
            }

        }

        return null;
    }

    /**
     * 
     * @param updateData
     * @throws InterruptedException
     */
    private void performAggregatedUpdate(final QuotaUpdate updateData) throws InterruptedException {

        final TileSet tileSet = updateData.getTileSet();

        TimedQuotaUpdate accumulatedUpdate = aggregatedDelayedUpdates.get(tileSet);

        if (accumulatedUpdate == null) {
            /*
             * it is the first one for this tile set, lets start the aggregated updates on it
             */
            accumulatedUpdate = new TimedQuotaUpdate(tileSet, tilePageCalculator);
            aggregatedDelayedUpdates.put(tileSet, accumulatedUpdate);
        }
        accumulatedUpdate.add(updateData);
    }

    /**
     * Makes sure no cached updates are held for too long before synchronizing with the store
     * 
     * @throws InterruptedException
     */
    private void checkAggregatedTimeouts() throws InterruptedException {
        if (aggregatedDelayedUpdates.size() == 0) {
            return;
        }
        List<TileSet> pruneList = null;
        for (TimedQuotaUpdate timedUpadte : aggregatedDelayedUpdates.values()) {
            if (pruneList == null) {
                pruneList = new ArrayList<TileSet>(2);
            }
            boolean prune = checkAggregatedTimeout(timedUpadte);
            if (prune) {
                pruneList.add(timedUpadte.getTileSet());
            }
        }
        prune(pruneList);
    }

    private void prune(List<TileSet> pruneList) {
        if (pruneList != null && pruneList.size() > 0) {
            for (TileSet ts : pruneList) {
                aggregatedDelayedUpdates.remove(ts);
            }
        }
    }

    /**
     * Makes sure the given cached updates are not held for too long before synchronizing with the
     * store, either because it's been held for too long, or because too many updates have happened
     * on it since the last time it was saved to the store.
     * 
     * @param timedUpadte
     * @return {@code true} if it's ok to prune the timedUpdate from the
     *         {@link #aggregatedDelayedUpdates local cache}
     * @throws InterruptedException
     */
    private boolean checkAggregatedTimeout(TimedQuotaUpdate timedUpadte)
            throws InterruptedException {
        final long creationTime = timedUpadte.creationTime;
        long timeSinceLastCommit = System.currentTimeMillis() - creationTime;
        boolean timeout = timeSinceLastCommit >= DEFAULT_SYNC_TIMEOUT;
        final int numAggregations = timedUpadte.numAggregations;
        boolean tooManyPendingCommits = numAggregations >= MAX_AGGREGATES_BEFORE_COMMIT;
        boolean canWaitABitLonger = timeSinceLastCommit < 2000
                && timedUpadte.tilePages.size() < 1000;
        if (!canWaitABitLonger && (timeout || tooManyPendingCommits)) {
            if (log.isDebugEnabled()) {
                log.debug("Committing "
                        + timedUpadte
                        + " to quota store due to "
                        + (tooManyPendingCommits ? "too many pending commits"
                                : "max wait time reached"));
            }
            commit(timedUpadte);
            return true;
        }

        return false;
    }

    private void commit(final TimedQuotaUpdate aggregatedUpadte) throws InterruptedException {
        final TileSet tileSet = aggregatedUpadte.getTileSet();
        final Quota quotaDiff = aggregatedUpadte.getAccummulatedQuotaDifference();

        Collection<PageStatsPayload> tileCountDiffs;
        tileCountDiffs = new ArrayList<PageStatsPayload>(
                aggregatedUpadte.getAccummulatedTilePageCounts());

        if (quotaDiff.getBytes().compareTo(BigInteger.ZERO) == 0 && tileCountDiffs.size() == 0) {
            return;
        }

        quotaStore.addToQuotaAndTileCounts(tileSet, quotaDiff, tileCountDiffs);
    }
    
    public void shutdown() {
        this.terminate = true;
    }
}
