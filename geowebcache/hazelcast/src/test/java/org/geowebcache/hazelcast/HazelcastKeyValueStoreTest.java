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
import static org.geowebcache.hazelcast.HazelcastTestMembers.CLUSTER;
import static org.geowebcache.hazelcast.HazelcastTestMembers.NO_FILE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.hazelcast.client.HazelcastClientOfflineException;
import com.hazelcast.core.HazelcastException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.OperationTimeoutException;
import com.hazelcast.map.IMap;
import com.hazelcast.map.MapInterceptor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import org.geowebcache.storage.memorycache.AbstractKeyValueStoreTest;
import org.geowebcache.storage.memorycache.KeyValueStore;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

/** Runs the store against a member started in the test JVM, reached through the client backend. */
public class HazelcastKeyValueStoreTest extends AbstractKeyValueStoreTest {

    private static HazelcastInstance member;

    private static HazelcastClientKeyValueStoreInfo settings;

    private final HazelcastClientConnection backend = new HazelcastClientConnection(NO_FILE);

    private final HazelcastClientConnection otherBackend = new HazelcastClientConnection(NO_FILE);

    @BeforeClass
    public static void startMember() {
        int port = HazelcastTestMembers.freePort();
        member = HazelcastTestMembers.start(port);
        settings = HazelcastTestMembers.clientSettings(port);
    }

    @AfterClass
    public static void stopMember() {
        member.shutdown();
    }

    @After
    public void shutdownClients() {
        backend.destroy();
        otherBackend.destroy();
    }

    @Override
    protected KeyValueStore createStore() {
        KeyValueStore created = connectedStore(backend);
        created.clear();
        return created;
    }

    /** The client starts in the background, calls fail until it connects. */
    private static KeyValueStore connectedStore(HazelcastClientConnection backend) {
        KeyValueStore created = backend.createStore(settings);
        await().atMost(30, SECONDS).ignoreExceptions().until(() -> created.get("probe") == null);
        return created;
    }

    @Test
    public void testExpiry() {
        store.put("key", new byte[] {1}, 1);

        await().atMost(10, SECONDS).until(() -> store.get("key") == null);
    }

    @Test
    public void testOtherClientSeesRemoval() {
        try (KeyValueStore other = connectedStore(otherBackend)) {
            store.put("layer/a/1", new byte[] {1}, 300);
            assertArrayEquals(new byte[] {1}, other.get("layer/a/1"));

            store.removeByPrefix("layer/");

            assertNull(other.get("layer/a/1"));
        }
    }

    @Test
    public void testUnreachableClusterFailsFast() {
        HazelcastClientKeyValueStoreInfo unreachable = new HazelcastClientKeyValueStoreInfo();
        unreachable.setClusterName(CLUSTER);
        unreachable.setMembers("127.0.0.1:1");

        try (KeyValueStore offline = otherBackend.createStore(unreachable)) {
            assertThrows(HazelcastClientOfflineException.class, () -> offline.get("key"));
        }
    }

    @Test
    public void testCloseKeepsSharedClient() {
        store.put("key", new byte[] {1}, 300);

        store.close();

        assertArrayEquals(new byte[] {1}, backend.createStore(settings).get("key"));
    }

    @Test
    public void testRemoveMatchingInBatches() {
        int count = HazelcastKeyValueStore.REMOVE_BATCH * 2 + 1;
        IntStream.range(0, count).forEach(i -> store.put("layer/" + i, new byte[] {1}, 300));

        store.removeMatching("layer/", key -> !key.equals("layer/0"));

        long present = IntStream.range(0, count)
                .filter(i -> store.get("layer/" + i) != null)
                .count();
        assertEquals(1, present);
    }

    @Test
    public void testRemoveMatchingWidenedPastLimit() {
        try (KeyValueStore limited = new HazelcastKeyValueStore(
                () -> member,
                settings.getMapName(),
                3,
                HazelcastKeyValueStore.CALL_TIMEOUT,
                HazelcastKeyValueStore.BULK_TIMEOUT,
                null)) {
            IntStream.range(0, 3).forEach(i -> store.put("layer/1/" + i, new byte[] {1}, 300));
            store.put("layer/2/0", new byte[] {1}, 300);

            // at the limit, only the matching keys go
            limited.removeMatching("layer/1/", key -> key.equals("layer/1/0"));
            assertNull(store.get("layer/1/0"));
            assertNotNull(store.get("layer/1/1"));

            // past it, every key under the prefix goes, and only those
            IntStream.range(3, 5).forEach(i -> store.put("layer/1/" + i, new byte[] {1}, 300));
            limited.removeMatching("layer/1/", key -> key.equals("layer/1/3"));
            assertNull(store.get("layer/1/1"));
            assertNull(store.get("layer/1/4"));
            assertNotNull(store.get("layer/2/0"));
        }
    }

    @Test
    public void testStalledMemberFailsWithinCallTimeout() {
        // own map, as the stalled partition thread holds every call on its partition
        IMap<String, byte[]> map = member.getMap("stalled-tiles");
        map.set("tile", Stall.VALUE);
        String interceptor = map.addInterceptor(new Stall());
        try (KeyValueStore stalled = new HazelcastKeyValueStore(
                () -> member, "stalled-tiles", HazelcastKeyValueStore.MATCHING_LIMIT, 200, 200, null)) {
            assertFailsFast(() -> stalled.get("tile"));
            assertFailsFast(() -> stalled.put("other", Stall.VALUE, 300));
            assertFailsFast(() -> stalled.putIfAbsent("absent", Stall.VALUE, 300));
            assertFailsFast(() -> stalled.remove("tile"));
        } finally {
            map.removeInterceptor(interceptor);
        }
    }

    @Test
    public void testRefusedIndexKeepsTruncating() {
        HazelcastInstance refusing = wrapMaps(map -> proxy(IMap.class, (s, m, a) -> refuseIndex(m, map, a)));
        try (KeyValueStore unindexed = new HazelcastKeyValueStore(
                () -> refusing, "unindexed-tiles", HazelcastKeyValueStore.MATCHING_LIMIT, 2000, 2000, null)) {
            unindexed.put("layer/a/1", new byte[] {1}, 300);
            unindexed.put("other/a/1", new byte[] {1}, 300);

            unindexed.removeByPrefix("layer/");
            unindexed.removeMatching("other/", key -> key.endsWith("/1"));

            assertNull(unindexed.get("layer/a/1"));
            assertNull(unindexed.get("other/a/1"));
        }
    }

    @Test
    public void testStalledBulkCallFailsWithinBulkTimeout() {
        CountDownLatch release = new CountDownLatch(1);
        HazelcastInstance stalling =
                wrapMaps(map -> proxy(IMap.class, (s, m, a) -> hold("removeAll", m, map, a, release)));
        try (KeyValueStore stalled = new HazelcastKeyValueStore(
                () -> stalling, "bulk-tiles", HazelcastKeyValueStore.MATCHING_LIMIT, 200, 200, null)) {
            assertFailsFast(() -> stalled.removeByPrefix("layer/"));
        } finally {
            release.countDown();
        }
    }

    @Test
    public void testPutIfAbsentSkippedWhileThreadsStalled() {
        CountDownLatch release = new CountDownLatch(1);
        HazelcastInstance stalling =
                wrapMaps(map -> proxy(IMap.class, (s, m, a) -> hold("putIfAbsent", m, map, a, release)));
        try (KeyValueStore stalled = new HazelcastKeyValueStore(
                () -> stalling, "put-tiles", HazelcastKeyValueStore.MATCHING_LIMIT, 50, 50, null)) {
            for (int i = 0; i < HazelcastKeyValueStore.PUT_THREADS; i++) {
                String key = "tile" + i;
                assertThrows(OperationTimeoutException.class, () -> stalled.putIfAbsent(key, Stall.VALUE, 300));
            }
            // every put thread still waits: the next copy is dropped without failing
            stalled.putIfAbsent("skipped", Stall.VALUE, 300);
        } finally {
            release.countDown();
        }
        assertNull(member.getMap("put-tiles").get("skipped"));
    }

    /** Calls {@code method} on {@code map}, {@code name} calls waiting for {@code release} as on a stalled member. */
    private static Object hold(String name, Method method, IMap<?, ?> map, Object[] args, CountDownLatch release)
            throws Throwable {
        if (method.getName().equals(name)) release.await();
        return call(method, map, args);
    }

    /** The test member, with each of its maps seen through {@code wrap}. */
    private static HazelcastInstance wrapMaps(UnaryOperator<IMap<?, ?>> wrap) {
        return proxy(HazelcastInstance.class, (self, method, args) -> {
            Object result = call(method, member, args);
            return result instanceof IMap<?, ?> map ? wrap.apply(map) : result;
        });
    }

    /** Calls {@code method} on {@code map}, refusing to add an index, as a cluster denying it would. */
    private static Object refuseIndex(Method method, IMap<?, ?> map, Object[] args) throws Throwable {
        if (method.getName().equals("addIndex")) throw new HazelcastException("Index refused");
        return call(method, map, args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object call(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static void assertFailsFast(ThrowingRunnable call) {
        long start = System.nanoTime();
        assertThrows(OperationTimeoutException.class, call);
        assertThat(NANOSECONDS.toMillis(System.nanoTime() - start), lessThan(Stall.MILLIS));
    }

    /** Holds the member partition thread on every read or write of {@link #VALUE}, as a stalled member does. */
    static class Stall implements MapInterceptor {

        static final byte[] VALUE = {42};

        static final long MILLIS = 1500;

        @Override
        public Object interceptGet(Object value) {
            stallOn(value);
            return null;
        }

        @Override
        public Object interceptPut(Object oldValue, Object newValue) {
            stallOn(newValue);
            return null;
        }

        private static void stallOn(Object value) {
            if (!(value instanceof byte[] bytes) || !Arrays.equals(bytes, VALUE)) return;
            try {
                Thread.sleep(MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void afterGet(Object value) {}

        @Override
        public Object interceptRemove(Object removedValue) {
            return null;
        }

        @Override
        public void afterPut(Object value) {}

        @Override
        public void afterRemove(Object oldValue) {}
    }
}
