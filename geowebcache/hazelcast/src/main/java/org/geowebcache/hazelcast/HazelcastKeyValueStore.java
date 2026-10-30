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
 * <p>Copyright 2026
 */
package org.geowebcache.hazelcast;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.Lists;
import com.hazelcast.aggregation.Aggregators;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.IndexType;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.OperationTimeoutException;
import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicates;
import com.hazelcast.query.QueryConstants;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.hazelcast.HazelcastLockProvider.Target;
import org.geowebcache.storage.memorycache.KeyValueStore;

/** {@link KeyValueStore} over one Hazelcast map shared by every node; the map and its client outlive {@link #close}. */
public class HazelcastKeyValueStore implements KeyValueStore {

    private static final String KEY = QueryConstants.KEY_ATTRIBUTE_NAME.value();

    private static final Logger LOGGER = Logging.getLogger(HazelcastKeyValueStore.class.getName());

    /** Keys per removeAll call, so one bounding box truncate does not build a single huge request. */
    static final int REMOVE_BATCH = 1000;

    /** Keys {@link #removeMatching} reads back at most, about 10-20 MB on this node. */
    static final long MATCHING_LIMIT = 100_000;

    /** System property, web.xml parameter or environment variable replacing {@link #MATCHING_LIMIT}. */
    static final String MATCHING_LIMIT_PROPERTY = "GWC_MEMORYCACHE_MATCHING_LIMIT";

    /** Milliseconds a tile read or write waits for the cluster before failing. */
    static final long CALL_TIMEOUT = 2000;

    /** System property, web.xml parameter or environment variable replacing {@link #CALL_TIMEOUT}. */
    static final String CALL_TIMEOUT_PROPERTY = "GWC_MEMORYCACHE_CALL_TIMEOUT";

    /** Milliseconds a truncate or clear call waits for the cluster before failing, long enough for a large removal. */
    static final long BULK_TIMEOUT = 10_000;

    /** System property, web.xml parameter or environment variable replacing {@link #BULK_TIMEOUT}. */
    static final String BULK_TIMEOUT_PROPERTY = "GWC_MEMORYCACHE_BULK_TIMEOUT";

    /** Bulk calls running at once per store, a few truncates; more are refused, failing at once. */
    static final int BULK_THREADS = 4;

    /** Read through copies written at once per store; more are skipped, as all threads waiting means a stall. */
    static final int PUT_THREADS = 16;

    private final Supplier<HazelcastInstance> instance;

    private final String mapName;

    private final long matchingLimit;

    private final long callTimeout;

    private final long bulkTimeout;

    /** Runs the bulk calls, which have no asynchronous variant, so that their caller can stop waiting. */
    private final ExecutorService bulkPool;

    /** Runs {@link #putIfAbsent}, which has no asynchronous variant either. */
    private final ExecutorService putPool;

    /** The cluster locks following this store, null without a lock provider. */
    @Nullable
    private final Target locks;

    /** Members seen when the index was last checked; the index lives on while one of them does. */
    private volatile Set<Member> indexedOn = Set.of();

    /** Whether a bounding box truncate was widened already, so that only the first one logs a warning. */
    private final AtomicBoolean widened = new AtomicBoolean();

    /** Whether adding the key index failed since it last succeeded, so that a failing index logs one warning. */
    private final AtomicBoolean indexFailing = new AtomicBoolean();

    /**
     * Stores in the named map; never contacts the cluster, so it can be built while the cluster is down. Calls fail
     * with the exception {@code instance} throws while the instance is not ready. Its reachability is passed on to
     * {@code locks}, which {@link #close} stops.
     */
    public HazelcastKeyValueStore(Supplier<HazelcastInstance> instance, String mapName, @Nullable Target locks) {
        this(
                instance,
                mapName,
                property(MATCHING_LIMIT_PROPERTY, MATCHING_LIMIT, 0),
                property(CALL_TIMEOUT_PROPERTY, CALL_TIMEOUT, 1),
                property(BULK_TIMEOUT_PROPERTY, BULK_TIMEOUT, 1),
                locks);
    }

    /** The value of {@code name}, {@code fallback} when unset, not a number or below {@code min}. */
    private static long property(String name, long fallback, long min) {
        String value = GeoWebCacheExtensions.getProperty(name);
        if (value == null) return fallback;
        long parsed;
        try {
            parsed = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            parsed = min - 1;
        }
        if (parsed >= min) return parsed;
        LOGGER.warning("Ignoring " + name + " " + value + ", not a number >= " + min);
        return fallback;
    }

    HazelcastKeyValueStore(
            Supplier<HazelcastInstance> instance,
            String mapName,
            long matchingLimit,
            long callTimeout,
            long bulkTimeout,
            @Nullable Target locks) {
        this.instance = instance;
        this.mapName = mapName;
        this.matchingLimit = matchingLimit;
        this.callTimeout = callTimeout;
        this.bulkTimeout = bulkTimeout;
        this.locks = locks;
        this.bulkPool = pool(BULK_THREADS, "gwc-hazelcast-bulk");
        this.putPool = pool(PUT_THREADS, "gwc-hazelcast-put");
    }

    /** Up to {@code threads} daemon threads, refusing a task when all are busy. */
    private static ExecutorService pool(int threads, String name) {
        // no queue: a call waiting for a thread would only use up its deadline
        return new ThreadPoolExecutor(0, threads, 60, SECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The first lookup creates the map on the cluster and fails while offline, later ones return the cached proxy. */
    private IMap<String, byte[]> map() {
        return instance.get().getMap(mapName);
    }

    /**
     * {@inheritDoc} Fails after {@link #CALL_TIMEOUT_PROPERTY} milliseconds, so a stalled member reads as an outage.
     */
    @Override
    public byte[] get(String key) {
        return await(map().getAsync(key), callTimeout);
    }

    /** {@inheritDoc} Fails after {@link #CALL_TIMEOUT_PROPERTY} milliseconds; the write may still land later. */
    @Override
    public void put(String key, byte[] value, long ttlSeconds) {
        // set does not return the previous value, put would send it back over the wire
        await(map().setAsync(key, value, ttlSeconds, SECONDS), callTimeout);
    }

    /**
     * {@inheritDoc} Fails after {@link #CALL_TIMEOUT_PROPERTY} milliseconds; the write may still land later. Skipped
     * without failing while {@link #PUT_THREADS} earlier ones still wait for the cluster.
     */
    @Override
    public void putIfAbsent(String key, byte[] value, long ttlSeconds) {
        IMap<String, byte[]> map = map();
        CompletableFuture<byte[]> call;
        try {
            // returns the value already there over the wire, no variant goes without it
            call = CompletableFuture.supplyAsync(() -> map.putIfAbsent(key, value, ttlSeconds, SECONDS), putPool);
        } catch (RejectedExecutionException e) {
            // a read through copy is optional: a later request reads the tile through again
            return;
        }
        await(call, callTimeout);
    }

    // the client invocation timeout only bounds retries after an error: a connected member that does not answer
    // keeps a synchronous call waiting until the heartbeat timeout, 60 s by default, under the blob store lock
    private static <T> T await(CompletionStage<T> call, long timeout) {
        try {
            return call.toCompletableFuture().get(timeout, MILLISECONDS);
        } catch (TimeoutException e) {
            throw new OperationTimeoutException("In-memory tile cache call got no answer in " + timeout + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the in-memory tile cache", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException(e.getCause());
        }
    }

    /** {@inheritDoc} Fails after {@link #CALL_TIMEOUT_PROPERTY} milliseconds; the removal may still land later. */
    @Override
    public void remove(String key) {
        // delete does not return the value, remove would send it back over the wire
        await(map().deleteAsync(key), callTimeout);
    }

    /** {@inheritDoc} Each cluster call fails after {@link #BULK_TIMEOUT_PROPERTY} milliseconds. */
    @Override
    public void removeByPrefix(String prefix) {
        IMap<String, byte[]> map = indexedMap();
        bulk(() -> map.removeAll(keyRange(prefix)));
    }

    /** Runs {@code call} on the bulk pool, failing after {@link #BULK_TIMEOUT_PROPERTY} milliseconds. */
    private void bulk(Runnable call) {
        await(CompletableFuture.runAsync(call, bulkPool), bulkTimeout);
    }

    /** Returns what {@code call} returns, run on the bulk pool and failing after {@link #BULK_TIMEOUT_PROPERTY}. */
    private <T> T bulkGet(Supplier<T> call) {
        return await(CompletableFuture.supplyAsync(call, bulkPool), bulkTimeout);
    }

    /**
     * {@inheritDoc} Removes the whole prefix when it holds more keys than {@link #MATCHING_LIMIT_PROPERTY} allows. Each
     * cluster call fails after {@link #BULK_TIMEOUT_PROPERTY} milliseconds.
     */
    @Override
    public void removeMatching(String prefix, Predicate<String> remove) {
        IMap<String, byte[]> map = indexedMap();
        var range = keyRange(prefix);
        // keys are read back to this node: past the limit, their memory costs more than re-rendering extra tiles
        long count = bulkGet(() -> map.aggregate(Aggregators.count(), range));
        if (count > matchingLimit) {
            bulk(() -> map.removeAll(range));
            String message = "In-memory tile cache removed all the " + count + " tiles under " + prefix
                    + " instead of the requested ones, as they are more than " + matchingLimit + " ("
                    + MATCHING_LIMIT_PROPERTY + ")";
            // a warning the first time, as production logging hides INFO; then INFO, as each zoom level may widen
            LOGGER.log(widened.getAndSet(true) ? Level.INFO : Level.WARNING, message);
            return;
        }
        // the predicate cannot run on the members, nothing of GWC is installed there
        List<String> keys =
                bulkGet(() -> map.keySet(range)).stream().filter(remove).toList();
        for (List<String> batch : Lists.partition(keys, REMOVE_BATCH)) {
            bulk(() -> map.removeAll(Predicates.in(KEY, batch.toArray(String[]::new))));
        }
    }

    /** Returns the map, with a sorted key index when it could be added, so a key range is a lookup, not a scan. */
    private IMap<String, byte[]> indexedMap() {
        HazelcastInstance hz = instance.get();
        IMap<String, byte[]> map = hz.getMap(mapName);
        // a full cluster restart drops the index; adding it is idempotent but a cluster wide call
        Set<Member> members = hz.getCluster().getMembers();
        if (!Collections.disjoint(members, indexedOn)) return map;
        try {
            bulk(() -> map.addIndex(IndexType.SORTED, KEY));
            indexedOn = members;
            indexFailing.set(false);
        } catch (OperationTimeoutException e) {
            // a cluster this slow fails the removal too, going on would only double the wait
            throw e;
        } catch (RuntimeException e) {
            // a key range then reads every entry on the members: slower, still correct; the next truncate tries again
            Level level = indexFailing.getAndSet(true) ? Level.FINE : Level.WARNING;
            LOGGER.log(
                    level,
                    "In-memory tile cache could not add the key index to map " + mapName + ", truncating without it",
                    e);
        }
        return map;
    }

    /** {@inheritDoc} Fails after {@link #BULK_TIMEOUT_PROPERTY} milliseconds. */
    @Override
    public void clear() {
        IMap<String, byte[]> map = map();
        bulk(map::clear);
    }

    /** {@inheritDoc} While unreachable, cluster locks give way to local ones. */
    @Override
    public void reachable(boolean reachable) {
        if (locks != null) locks.reachable(reachable);
    }

    /**
     * {@inheritDoc} Leaves the shared client to its connection, and stops the cluster locks of this store. Bulk calls
     * and writes still running end on their own.
     */
    @Override
    public void close() {
        bulkPool.shutdown();
        putPool.shutdown();
        if (locks != null) locks.stop();
    }

    /** Keys starting with the prefix are exactly those between it and the prefix with its last char incremented. */
    private static com.hazelcast.query.Predicate<String, byte[]> keyRange(String prefix) {
        int last = prefix.length() - 1;
        String end = prefix.substring(0, last) + (char) (prefix.charAt(last) + 1);
        return Predicates.and(Predicates.greaterEqual(KEY, prefix), Predicates.lessThan(KEY, end));
    }
}
