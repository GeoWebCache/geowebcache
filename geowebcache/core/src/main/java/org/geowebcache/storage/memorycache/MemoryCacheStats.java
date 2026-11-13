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
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

/** Per-layer read results and failed puts, logged at INFO and reset by the first {@link #report()} of each interval. */
class MemoryCacheStats {

    private static final Logger LOGGER = Logging.getLogger(MemoryCacheStats.class.getName());

    /** Outcome of a tile read. */
    enum Result {
        /** Served from the cache. */
        HIT,
        /** Not in the cache, read through from the store behind. */
        STORE,
        /** Neither in the cache nor, with passthrough on, in the store behind. */
        MISS,
        /** Served from the cache, but older than the layer's expire server cache, so the layer renders it again. */
        EXPIRED,
        /** The cache read failed. */
        ERROR
    }

    private final long intervalNanos;

    private final Ticker ticker;

    private final AtomicLong nextReport;

    private final Map<String, LayerStats> layers = new ConcurrentHashMap<>();

    /** Counters shared by every layer when reports are off, never read. */
    private final LayerStats discarded = new LayerStats();

    /** No report is ever logged with an interval of 0. */
    MemoryCacheStats(long intervalSeconds, Ticker ticker) {
        this.intervalNanos = SECONDS.toNanos(intervalSeconds);
        this.ticker = ticker;
        this.nextReport = new AtomicLong(ticker.read() + intervalNanos);
    }

    LayerStats forLayer(String layerName) {
        // never reported, so never pruned: keeping them per layer would only grow with removed and renamed layers
        if (intervalNanos <= 0) return discarded;
        return layers.computeIfAbsent(layerName, name -> new LayerStats());
    }

    /**
     * Logs and resets the counters of every layer with traffic when the interval is over, cheap otherwise. A layer
     * without traffic in the interval is dropped, so removed and renamed layers do not stay; a read racing the drop may
     * go uncounted.
     */
    void report() {
        if (intervalNanos <= 0) return;
        long now = ticker.read();
        long next = nextReport.get();
        // one caller wins the interval, the others go on serving tiles
        if (now - next < 0 || !nextReport.compareAndSet(next, now + intervalNanos)) return;
        long elapsedSeconds = NANOSECONDS.toSeconds(now - next + intervalNanos);
        layers.forEach((name, stats) -> {
            if (!stats.report(name, elapsedSeconds)) layers.remove(name, stats);
        });
    }

    /** Counters of one layer, each reset on its own, so a call racing with a report may count in the next interval. */
    static class LayerStats {

        /** Filled in the constructor and never changed after, so reads need no lock. */
        private final Map<Result, LongAdder> reads = new EnumMap<>(Result.class);

        private final LongAdder failedPuts = new LongAdder();

        LayerStats() {
            for (Result result : Result.values()) reads.put(result, new LongAdder());
        }

        void read(Result result) {
            reads.get(result).increment();
        }

        void failedPut() {
            failedPuts.increment();
        }

        /** Logs and resets the counters, returning false, with nothing logged, when there was no traffic. */
        private boolean report(String layerName, long elapsedSeconds) {
            Map<Result, Long> counts = new EnumMap<>(Result.class);
            reads.forEach((result, adder) -> counts.put(result, adder.sumThenReset()));
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            long failed = failedPuts.sumThenReset();
            if (total + failed == 0) return false;

            long hits = counts.get(Result.HIT);
            String message = String.format(
                    Locale.ROOT,
                    "In-memory tile cache stats for layer %s over %d s: %d reads (hit %d, store %d, miss %d,"
                            + " expired %d, error %d), hit ratio %.1f%%, %d failed puts",
                    layerName,
                    elapsedSeconds,
                    total,
                    hits,
                    counts.get(Result.STORE),
                    counts.get(Result.MISS),
                    counts.get(Result.EXPIRED),
                    counts.get(Result.ERROR),
                    total == 0 ? 0 : 100.0 * hits / total,
                    failed);
            LOGGER.info(message);
            return true;
        }
    }
}
