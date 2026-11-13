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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Ticker;
import java.io.Serial;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.geotools.util.logging.Logging;

/**
 * Stops calling a failing backend for a while, so that a stalled cluster does not cost every tile request a full
 * timeout. Failures, and calls refused meanwhile, throw {@link UnavailableException}; the backend hears of each change
 * through {@link KeyValueStore#reachable}. Thread safe.
 *
 * <pre>
 *   UP --fail--&gt; DOWN --wait over--&gt; PROBING --ok--&gt; UP
 *                  ^                     |
 *                  +-------fail----------+
 * </pre>
 *
 * <p>While probing, a single call tests the backend. The wait grows with the outage, and a failure soon after a
 * recovery continues the same outage. A call that started before the last change does not move the state.
 */
class GuardedStore implements KeyValueStore {

    private static final Logger LOGGER = Logging.getLogger(GuardedStore.class.getName());

    /** Shortest wait between two probes. */
    private static final long MIN_RETRY = SECONDS.toNanos(1);

    /** Longest wait between two probes, also how long a recovery must hold before a failure starts a new outage. */
    private static final long MAX_RETRY = SECONDS.toNanos(30);

    /** The backend health, all times in ticker nanos. */
    private sealed interface State permits Up, Down, Probing {}

    /** Calls go through. {@code outageSince} and {@code recoveredAt} describe the last outage. */
    private record Up(long outageSince, long recoveredAt) implements State {}

    /** Calls are refused until {@code retryAt}. */
    private record Down(long since, long retryAt) implements State {}

    /**
     * One call, granted at {@code grantedAt}, is testing the backend. Others are refused until it reports, or until
     * {@code deadline} if it never does (a hung or lost probe), when the next caller probes instead.
     */
    private record Probing(long since, long grantedAt, long deadline) implements State {}

    private final AtomicReference<State> state;

    /** Null when unusable. */
    private final KeyValueStore delegate;

    /** Why the backend could not be built, null when usable. */
    private final String unusableReason;

    private final Ticker ticker;

    GuardedStore(KeyValueStore delegate, Ticker ticker) {
        this(delegate, null, ticker);
    }

    private GuardedStore(@Nullable KeyValueStore delegate, @Nullable String unusableReason, Ticker ticker) {
        this.delegate = delegate;
        this.unusableReason = unusableReason;
        this.ticker = ticker;
        // as if recovered long enough ago: every later failure starts a new outage
        this.state = new AtomicReference<>(new Up(0, ticker.read() - MAX_RETRY));
    }

    /** A store refusing every call, for settings that cannot work until they change. Logs the reason once. */
    static GuardedStore unusable(String reason, @Nullable Throwable cause) {
        LOGGER.log(Level.SEVERE, "In-memory tile cache unusable, " + reason, cause);
        return new GuardedStore(null, reason, Ticker.systemTicker());
    }

    /** Whether the settings left no backend: the store never held tiles, and never recovers until they change. */
    boolean isUnusable() {
        return delegate == null;
    }

    /**
     * Whether a call may go to the backend now. DOWN, or a PROBING past its deadline, moves to PROBING for one caller.
     */
    private boolean mayCall() {
        State current = state.get();
        if (current instanceof Up) return true;
        long now = ticker.read();
        long since;
        if (current instanceof Down down && now - down.retryAt() >= 0) {
            since = down.since();
        } else if (current instanceof Probing probing && now - probing.deadline() >= 0) {
            since = probing.since();
        } else {
            return false;
        }
        // the caller winning the swap is the probe, the others keep being refused
        return state.compareAndSet(current, new Probing(since, now, now + backoff(since, now)));
    }

    /** DOWN or PROBING to UP, unless the call started before the outage. */
    private void succeeded(long start) {
        State current = state.get();
        long since;
        if (current instanceof Down down) {
            since = down.since();
        } else if (current instanceof Probing probing) {
            since = probing.since();
        } else {
            return;
        }
        // on a cluster a call can answer after a newer one failed on another member: it says nothing about the outage
        if (start - since < 0) return;
        long now = ticker.read();
        if (state.compareAndSet(current, new Up(since, now))) {
            LOGGER.severe("In-memory tile cache is reachable again after " + NANOSECONDS.toSeconds(now - since) + " s");
            reportState();
        }
    }

    /** UP to DOWN, starting or resuming an outage; a failed probe back to DOWN with a longer wait. */
    private void failed(long start, RuntimeException e) {
        long now = ticker.read();
        while (true) {
            State current = state.get();
            long since;
            boolean resumed;
            if (current instanceof Probing probing && start - probing.grantedAt() >= 0) {
                since = probing.since();
                resumed = false;
            } else if (current instanceof Up up && start - up.recoveredAt() >= 0) {
                resumed = now - up.recoveredAt() < MAX_RETRY;
                since = resumed ? up.outageSince() : now;
            } else {
                // already DOWN, or a call started before the last change, failing on the state before it
                return;
            }
            if (state.compareAndSet(current, new Down(since, now + backoff(since, now)))) {
                logFailure(current, resumed, e);
                // a failed probe stays unreachable, nothing to report
                if (current instanceof Up) reportState();
                return;
            }
        }
    }

    /**
     * Tells the backend whether it is reachable. Reads the state under the lock, so that two changes racing cannot
     * leave the older one reported last.
     */
    private synchronized void reportState() {
        delegate.reachable(state.get() instanceof Up);
    }

    /**
     * Logs an outage start or resume at SEVERE, as production logging keeps org.geowebcache at error and an outage
     * start without its end cannot be read; a failed probe is not logged, the outage is already.
     */
    private static void logFailure(State previous, boolean resumed, RuntimeException e) {
        if (!(previous instanceof Up)) return;
        if (resumed) {
            LOGGER.severe("In-memory tile cache is unreachable again");
            return;
        }
        LOGGER.log(
                Level.SEVERE, "In-memory tile cache is unreachable, tiles are served without it until it recovers", e);
    }

    /** Half the outage age within the retry bounds, with 20% jitter so that nodes do not probe in lockstep. */
    private static long backoff(long since, long now) {
        long wait = Math.min(Math.max((now - since) / 2, MIN_RETRY), MAX_RETRY);
        return (long) (wait * ThreadLocalRandom.current().nextDouble(0.8, 1.2));
    }

    /** Thrown for every failed or refused call. No stack trace: while down it is thrown once per tile request. */
    static class UnavailableException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        UnavailableException(String message, @Nullable Throwable cause) {
            super(message, cause, false, false);
        }
    }

    private <T> T call(Function<KeyValueStore, T> operation) {
        // permanent, outside the state machine: no probe, no outage log, the reason was logged on creation
        if (delegate == null) throw new UnavailableException("In-memory tile cache unusable, " + unusableReason, null);
        if (!mayCall()) throw new UnavailableException("In-memory tile cache is down", null);
        long start = ticker.read();
        try {
            T result = operation.apply(delegate);
            succeeded(start);
            return result;
        } catch (RuntimeException e) {
            failed(start, e);
            throw new UnavailableException("In-memory tile cache call failed", e);
        }
    }

    private void run(Consumer<KeyValueStore> operation) {
        call(store -> {
            operation.accept(store);
            return null;
        });
    }

    @Override
    public byte[] get(String key) {
        return call(store -> store.get(key));
    }

    @Override
    public void put(String key, byte[] value, long ttlSeconds) {
        run(store -> store.put(key, value, ttlSeconds));
    }

    @Override
    public void putIfAbsent(String key, byte[] value, long ttlSeconds) {
        run(store -> store.putIfAbsent(key, value, ttlSeconds));
    }

    @Override
    public void remove(String key) {
        run(store -> store.remove(key));
    }

    @Override
    public void removeByPrefix(String prefix) {
        run(store -> store.removeByPrefix(prefix));
    }

    @Override
    public void removeMatching(String prefix, Predicate<String> remove) {
        run(store -> store.removeMatching(prefix, remove));
    }

    @Override
    public void clear() {
        run(KeyValueStore::clear);
    }

    /** Closes the backend store; requests still running on it see failures, handled as misses. */
    @Override
    public void close() {
        if (delegate != null) delegate.close();
    }
}
