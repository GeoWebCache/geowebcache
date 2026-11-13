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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.geowebcache.hazelcast.HazelcastClientKeyValueStoreInfo.DEFAULT_MAP_NAME;
import static org.geowebcache.hazelcast.HazelcastTestMembers.NO_FILE;
import static org.geowebcache.hazelcast.HazelcastTestMembers.clientSettings;
import static org.geowebcache.hazelcast.HazelcastTestMembers.freePort;
import static org.geowebcache.hazelcast.HazelcastTestMembers.start;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.storage.memorycache.KeyValueStore;
import org.junit.After;
import org.junit.Test;

/** Locks shared by nodes connected as clients to the same members, with their fallbacks to local locks. */
public class HazelcastLockProviderTest {

    private static final String KEY = "gwc_metatile_test";

    private final List<HazelcastClientConnection> backends = new ArrayList<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @After
    public void shutdown() {
        executor.shutdownNow();
        backends.forEach(HazelcastClientConnection::destroy);
        Hazelcast.shutdownAll();
    }

    /** A node with its own client, connected, waiting for and leasing locks {@code timeout} seconds. */
    private HazelcastLockProvider node(int port, int timeout) {
        HazelcastLockProvider locks = new HazelcastLockProvider(timeout);
        connect(locks, port);
        return locks;
    }

    /** The store of a new client locking through {@code locks}, once connected. */
    private KeyValueStore connect(HazelcastLockProvider locks, int port) {
        HazelcastClientConnection backend = new HazelcastClientConnection(NO_FILE);
        backend.setLockProvider(locks);
        backends.add(backend);
        KeyValueStore store = backend.createStore(clientSettings(port));
        await().atMost(30, SECONDS).ignoreExceptions().until(() -> store.get("probe") == null);
        return store;
    }

    private static IMap<Object, Object> locks(HazelcastInstance member) {
        return member.getMap(DEFAULT_MAP_NAME + ".locks");
    }

    @Test
    public void testLockExcludesOtherNodes() throws Exception {
        int port = freePort();
        HazelcastInstance member = start(port);
        HazelcastLockProvider first = node(port, 60);
        HazelcastLockProvider second = node(port, 60);
        Lock held = first.getLock(KEY);

        // released on the thread that acquired it, as Hazelcast requires
        Future<Boolean> waiting = executor.submit(() -> {
            Lock lock = second.getLock(KEY);
            boolean onCluster = locks(member).isLocked(KEY);
            lock.release();
            return onCluster;
        });

        // past the 5 s client invocation timeout, which must not cut a waiting lock short
        await().during(7, SECONDS).atMost(10, SECONDS).until(() -> !waiting.isDone());
        held.release();
        assertTrue(waiting.get(10, SECONDS));
        assertFalse(locks(member).isLocked(KEY));
    }

    @Test
    public void testLeaseFreesLockNeverReleased() throws Exception {
        int port = freePort();
        HazelcastInstance member = start(port);
        Lock stale = node(port, 2).getLock(KEY);
        HazelcastLockProvider second = node(port, 30);

        long start = System.nanoTime();
        Lock lock = second.getLock(KEY);
        assertThat(NANOSECONDS.toSeconds(System.nanoTime() - start), lessThan(10L));

        // a late release by the first holder is logged, and leaves the lock of the second one alone
        stale.release();
        assertTrue(locks(member).isLocked(KEY));
        lock.release();
        assertFalse(locks(member).isLocked(KEY));
    }

    @Test
    public void testTimeoutFallsBackToLocalLock() throws Exception {
        int port = freePort();
        start(port);
        Lock held = node(port, 60).getLock(KEY);
        HazelcastLockProvider impatient = node(port, 1);

        long start = System.nanoTime();
        impatient.getLock(KEY).release();
        assertThat(NANOSECONDS.toSeconds(System.nanoTime() - start), lessThan(5L));
        held.release();
    }

    @Test
    public void testClusterDownFallsBackToLocalLock() throws Exception {
        int port = freePort();
        HazelcastInstance member = start(port);
        HazelcastLockProvider locks = node(port, 60);

        member.shutdown();

        // once the client knows, locks fall back at once instead of waiting on the cluster
        await().atMost(30, SECONDS).until(() -> lockMillis(locks) < 1000);
        // and still exclude other threads of this node
        Lock held = locks.getLock(KEY);
        Future<?> waiting = executor.submit(() -> {
            locks.getLock(KEY).release();
            return null;
        });
        await().during(1, SECONDS).atMost(2, SECONDS).until(() -> !waiting.isDone());
        held.release();
        waiting.get(10, SECONDS);
    }

    @Test
    @SuppressWarnings("PMD.CloseResource") // closes the stores by hand, in the order under test
    public void testClosedStoreStopsClusterLocks() throws Exception {
        int port = freePort();
        HazelcastInstance member = start(port);
        HazelcastLockProvider locks = new HazelcastLockProvider(60);
        HazelcastClientConnection backend = new HazelcastClientConnection(NO_FILE);
        backend.setLockProvider(locks);
        backends.add(backend);
        KeyValueStore first = backend.createStore(clientSettings(port));
        await().atMost(30, SECONDS).ignoreExceptions().until(() -> first.get("probe") == null);

        // settings saved again: the new store is built before the old one is closed, and keeps the cluster locks
        KeyValueStore second = backend.createStore(clientSettings(port));
        first.close();
        assertTrue(lockedOnCluster(locks, member));

        // the cache switched to another backend
        second.close();
        assertFalse(lockedOnCluster(locks, member));
    }

    @Test
    public void testUnreachableStoreLocksLocally() throws Exception {
        int port = freePort();
        HazelcastInstance member = start(port);
        HazelcastLockProvider locks = new HazelcastLockProvider(60);
        try (KeyValueStore store = connect(locks, port)) {
            // the tile cache found the cluster down: locks skip it until the cache finds it back
            store.reachable(false);
            assertFalse(lockedOnCluster(locks, member));
            store.reachable(true);
            assertTrue(lockedOnCluster(locks, member));
        }
    }

    private static boolean lockedOnCluster(HazelcastLockProvider locks, HazelcastInstance member) throws Exception {
        Lock lock = locks.getLock(KEY);
        try {
            return locks(member).isLocked(KEY);
        } finally {
            lock.release();
        }
    }

    private static long lockMillis(HazelcastLockProvider locks) throws Exception {
        long start = System.nanoTime();
        locks.getLock(KEY).release();
        return NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
