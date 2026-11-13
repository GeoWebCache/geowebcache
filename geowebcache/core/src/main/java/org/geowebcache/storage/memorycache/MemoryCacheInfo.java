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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** The {@code <memoryCache>} settings of {@code geowebcache.xml}; an absent element reads as its default. */
public class MemoryCacheInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final long DEFAULT_TIME_TO_LIVE = 300;

    static final long DEFAULT_STATS_INTERVAL = 300;

    private Boolean enabled;

    private Long timeToLive;

    private Boolean passthrough;

    private Long statsInterval;

    private KeyValueStoreInfo backend;

    /** Whether cached layers go through the cache, false by default. */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /** Turns the cache on or off for every cached layer. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Time to live in seconds of the tiles of layers without an expire server cache, 300 by default. */
    public long getTimeToLive() {
        return timeToLive == null ? DEFAULT_TIME_TO_LIVE : timeToLive;
    }

    /** Sets the time to live of the tiles of layers without an expire server cache, in seconds and positive. */
    public void setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
    }

    /** Whether tiles are also written to, and read through from, the layer's own blob store, true by default. */
    public boolean isPassthrough() {
        return passthrough == null || passthrough;
    }

    /** Sets whether tiles are also written to, and read through from, the layer's own blob store. */
    public void setPassthrough(boolean passthrough) {
        this.passthrough = passthrough;
    }

    /** Seconds between two statistics lines per layer in the log, 300 by default, 0 when turned off. */
    public long getStatsInterval() {
        return statsInterval == null ? DEFAULT_STATS_INTERVAL : statsInterval;
    }

    /** Sets the seconds between two statistics lines per layer in the log, 0 turns them off. */
    public void setStatsInterval(long statsInterval) {
        this.statsInterval = statsInterval;
    }

    /** The backend settings, the heap backend with its defaults when absent. */
    public KeyValueStoreInfo getBackend() {
        return backend == null ? new HeapKeyValueStoreInfo() : backend;
    }

    /** Sets the backend settings, which create the store of the cache. */
    public void setBackend(KeyValueStoreInfo backend) {
        this.backend = backend;
    }

    /** Equal when the effective settings are, an absent value matching its default. */
    @Override
    public boolean equals(Object o) {
        return o instanceof MemoryCacheInfo other
                && isEnabled() == other.isEnabled()
                && getTimeToLive() == other.getTimeToLive()
                && isPassthrough() == other.isPassthrough()
                && getStatsInterval() == other.getStatsInterval()
                && getBackend().equals(other.getBackend());
    }

    @Override
    public int hashCode() {
        return Objects.hash(isEnabled(), getTimeToLive(), isPassthrough(), getStatsInterval(), getBackend());
    }
}
