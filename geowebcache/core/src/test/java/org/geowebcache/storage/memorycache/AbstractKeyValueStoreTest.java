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
package org.geowebcache.storage.memorycache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** {@link KeyValueStore} contract every backend must pass; subclasses provide an empty store. */
public abstract class AbstractKeyValueStoreTest {

    /** "layer/a0/1" sits right after the "layer/a/" range, as '0' follows '/'. */
    private static final List<String> KEYS = List.of("layer/a/1", "layer/a/2", "layer/a0/1", "layer/ab/1", "other/1");

    private static final int THREADS = 4;

    private static final int ROUNDS = 200;

    private static final int VALUE_SIZE = 16 * 1024;

    protected KeyValueStore store;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** Released once every task of a test is submitted, so that they start together. */
    private final CountDownLatch go = new CountDownLatch(1);

    /** Returns a store holding no entries. */
    protected abstract KeyValueStore createStore() throws Exception;

    @Before
    public void setUpStore() throws Exception {
        store = createStore();
    }

    @After
    public void closeStore() {
        executor.shutdownNow();
        // null when createStore failed, keep that failure as the only one reported
        if (store != null) store.close();
    }

    @Test
    public void testPutOverwrites() {
        store.put("key", new byte[] {1}, 300);
        store.put("key", new byte[] {2}, 300);

        assertArrayEquals(new byte[] {2}, store.get("key"));
        assertNull(store.get("missing"));
    }

    @Test
    public void testRemove() {
        store.put("key", new byte[] {1}, 300);

        store.remove("key");
        store.remove("key");
        assertNull(store.get("key"));
    }

    @Test
    public void testRemoveByPrefix() {
        putKeys();

        store.removeByPrefix("layer/a/");

        assertThat(presentKeys(), containsInAnyOrder("layer/a0/1", "layer/ab/1", "other/1"));
    }

    @Test
    public void testRemoveMatching() {
        putKeys();

        store.removeMatching("layer/", key -> key.endsWith("/1"));

        assertThat(presentKeys(), containsInAnyOrder("layer/a/2", "other/1"));
    }

    @Test
    public void testClear() {
        putKeys();

        store.clear();

        assertThat(presentKeys(), empty());
    }

    @Test
    public void testPutIfAbsentKeepsPresentValue() {
        store.put("key", new byte[] {1}, 300);
        store.putIfAbsent("key", new byte[] {2}, 300);
        store.putIfAbsent("other", new byte[] {3}, 300);

        assertArrayEquals(new byte[] {1}, store.get("key"));
        assertArrayEquals(new byte[] {3}, store.get("other"));
    }

    @Test
    public void testConcurrentGetsAndPutsGiveWholeValues() throws Exception {
        List<Future<?>> tasks = new ArrayList<>();
        AtomicInteger broken = new AtomicInteger();
        for (int t = 1; t <= THREADS; t++) {
            byte[] value = new byte[VALUE_SIZE];
            Arrays.fill(value, (byte) t);
            tasks.add(submit(() -> {
                for (int i = 0; i < ROUNDS; i++) store.put("key", value.clone(), 300);
            }));
            tasks.add(submit(() -> {
                for (int i = 0; i < ROUNDS; i++) if (!isWhole(store.get("key"))) broken.incrementAndGet();
            }));
        }
        go.countDown();
        for (Future<?> task : tasks) task.get(60, SECONDS);

        assertEquals(0, broken.get());
    }

    /** Null, or the value of one writer: full length, every byte its id. */
    private static boolean isWhole(byte[] value) {
        if (value == null) return true;
        if (value.length != VALUE_SIZE || value[0] == 0) return false;
        for (byte b : value) if (b != value[0]) return false;
        return true;
    }

    @Test
    public void testRemoveByPrefixDuringPutsLeavesNoEarlierEntry() throws Exception {
        AtomicBoolean removing = new AtomicBoolean();
        AtomicBoolean done = new AtomicBoolean();
        Queue<String> before = new ConcurrentLinkedQueue<>();
        CountDownLatch warm = new CountDownLatch(THREADS);
        List<Future<?>> writers = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            String prefix = "layer/a/" + t + "/";
            writers.add(submit(() -> {
                for (int i = 0; !done.get(); i++) {
                    store.put(prefix + i, new byte[] {1}, 300);
                    // false after the put returned: it ended before the removal started
                    if (!removing.get()) before.add(prefix + i);
                    if (i == ROUNDS / 4) warm.countDown();
                }
            }));
        }
        go.countDown();
        assertTrue(warm.await(60, SECONDS));
        removing.set(true);
        store.removeByPrefix("layer/a/");
        done.set(true);
        for (Future<?> writer : writers) writer.get(60, SECONDS);

        assertThat(present(before), empty());
    }

    private Future<?> submit(Runnable task) {
        return executor.submit((Callable<Void>) () -> {
            go.await();
            task.run();
            return null;
        });
    }

    private void putKeys() {
        KEYS.forEach(key -> store.put(key, new byte[] {1}, 300));
    }

    private List<String> presentKeys() {
        return present(KEYS);
    }

    private List<String> present(Collection<String> keys) {
        return keys.stream().filter(key -> store.get(key) != null).toList();
    }
}
