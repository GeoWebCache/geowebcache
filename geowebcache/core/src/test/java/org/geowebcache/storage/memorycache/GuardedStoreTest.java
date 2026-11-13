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
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Ticker;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.geowebcache.storage.memorycache.GuardedStore.UnavailableException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

/** The outage state machine, on a store driven by a test ticker. */
public class GuardedStoreTest {

    @Rule
    public LogLines log = new LogLines(GuardedStore.class, Level.INFO);

    private final AtomicLong now = new AtomicLong();

    private final Ticker ticker = new Ticker() {
        @Override
        public long read() {
            return now.get();
        }
    };

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final AtomicInteger calls = new AtomicInteger();

    private final CountDownLatch entered = new CountDownLatch(1);

    private final CountDownLatch release = new CountDownLatch(1);

    private final List<Boolean> reports = new CopyOnWriteArrayList<>();

    /**
     * A heap store whose {@code get} fails on "fail" and waits for {@link #release} on "slow", counting the calls and
     * recording the reachability reports.
     */
    private final GuardedStore store = new GuardedStore(
            new HeapKeyValueStore(1_000_000, ticker) {
                @Override
                public byte[] get(String key) {
                    calls.incrementAndGet();
                    if (key.equals("fail")) throw new IllegalStateException("Simulated cache failure");
                    if (key.equals("slow")) await();
                    return super.get(key);
                }

                @Override
                public void reachable(boolean reachable) {
                    reports.add(reachable);
                }

                private void await() {
                    entered.countDown();
                    try {
                        assertTrue(release.await(10, SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            },
            ticker);

    @After
    public void stopExecutor() {
        release.countDown();
        executor.shutdownNow();
    }

    @Test
    public void testHungProbeLetsNextCallerProbe() throws Exception {
        failAt(0);
        // the probe, granted after the first retry interval (1 s plus jitter), never answers
        now.set(SECONDS.toNanos(2));
        Future<byte[]> probe = executor.submit(() -> store.get("slow"));
        assertTrue(entered.await(10, SECONDS));
        assertThrows(UnavailableException.class, () -> store.get("key"));

        // past the probe deadline the next caller probes, and its success ends the outage
        now.set(SECONDS.toNanos(4));
        store.get("key");
        store.get("key");
        assertEquals(4, calls.get());
        release.countDown();
        probe.get(10, SECONDS);
    }

    @Test
    public void testCallStartedBeforeOutageReportsNothing() throws Exception {
        Future<byte[]> old = executor.submit(() -> store.get("slow"));
        assertTrue(entered.await(10, SECONDS));
        failAt(1);

        // the old call succeeding later does not end the outage
        release.countDown();
        old.get(10, SECONDS);
        assertThrows(UnavailableException.class, () -> store.get("key"));
        assertEquals(2, calls.get());
    }

    @Test
    public void testStateChangesReportedOnce() {
        failAt(0);
        assertThrows(UnavailableException.class, () -> store.get("key"));
        // a failed probe leaves it unreachable, nothing new to report
        now.set(SECONDS.toNanos(2));
        assertThrows(UnavailableException.class, () -> store.get("fail"));

        now.set(SECONDS.toNanos(10));
        store.get("key");
        store.get("key");

        assertThat(reports, contains(false, true));
    }

    @Test
    public void testUnusableNeverProbes() {
        try (GuardedStore unusable = GuardedStore.unusable("backend missing is not installed", null)) {
            now.set(SECONDS.toNanos(60));
            for (int i = 0; i < 3; i++) assertThrows(UnavailableException.class, () -> unusable.get("key"));

            assertTrue(unusable.isUnusable());
        }
        // logged once on creation, no outage
        assertThat(log.lines, contains("In-memory tile cache unusable, backend missing is not installed"));
    }

    /** A failing call at {@code seconds}, starting an outage. */
    private void failAt(long seconds) {
        now.set(SECONDS.toNanos(seconds));
        assertThrows(UnavailableException.class, () -> store.get("fail"));
    }
}
