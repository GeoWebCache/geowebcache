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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.MemoryLockProvider;

/**
 * {@link LockProvider} shared by every node of the Hazelcast tile cache, so a metatile is rendered once in the cluster.
 * When the cluster cannot be reached, the tile cache finds it down, or a lock is not acquired in time, it falls back to
 * a local lock: the worst case is a tile rendered once per node, never a failed request. A lock must be released by the
 * thread that acquired it.
 */
public class HazelcastLockProvider implements LockProvider {

    private static final Logger LOGGER = Logging.getLogger(HazelcastLockProvider.class.getName());

    private final MemoryLockProvider local = new MemoryLockProvider();

    private final AtomicBoolean warnedNoCluster = new AtomicBoolean();

    /** Seconds to wait for a lock, also the lease freeing a lock its holder never released. */
    private final int timeout;

    /** Where to lock, null while no store of a Hazelcast tile cache backend is in use. */
    private final AtomicReference<Target> locks = new AtomicReference<>();

    /** Waits for a lock, and keeps it at most, {@link LockProvider#GWC_LOCK_TIMEOUT} seconds. */
    public HazelcastLockProvider() {
        this(GWC_LOCK_TIMEOUT);
    }

    HazelcastLockProvider(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Locks on {@code instance} from now on, in a map named after the tile map, until the returned target stops or
     * another call takes over.
     */
    Target lockOn(Supplier<HazelcastInstance> instance, String tileMap) {
        // map locks create no entries, so this map needs no declaration on the cluster
        String name = tileMap + ".locks";
        Target target = new Target(() -> instance.get().getMap(name));
        locks.set(target);
        warnedNoCluster.set(false);
        return target;
    }

    /** The lock map of one tile cache store, skipped while the cache finds that store unreachable. */
    public final class Target {

        private final Supplier<IMap<String, Object>> map;

        private volatile boolean reachable = true;

        private Target(Supplier<IMap<String, Object>> map) {
            this.map = map;
        }

        void reachable(boolean reachable) {
            this.reachable = reachable;
        }

        /** Back to local locks, unless another store took over. */
        void stop() {
            locks.compareAndSet(this, null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Lock getLock(String lockKey) throws GeoWebCacheException {
        Target current = locks.get();
        if (current == null) {
            if (warnedNoCluster.compareAndSet(false, true)) {
                LOGGER.warning("Hazelcast locking needs the tile cache enabled with a Hazelcast backend,"
                        + " using local locks until then");
            }
            return local.getLock(lockKey);
        }
        // the cache logged the outage already, and waiting on a silent cluster would cost every metatile a timeout
        if (!current.reachable) return local.getLock(lockKey);
        try {
            IMap<String, Object> map = current.map.get();
            // map locks rather than the CP FencedLock, which has no consistency guarantee without CP members
            if (map.tryLock(lockKey, timeout, SECONDS, timeout, SECONDS)) return () -> release(map, lockKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeoWebCacheException("Interrupted while acquiring lock " + lockKey, e);
        } catch (RuntimeException e) {
            // fails at once while the client is offline, the tile cache already logs the outage
            LOGGER.log(Level.FINE, e, () -> "Hazelcast unreachable, local lock for " + lockKey);
            return local.getLock(lockKey);
        }
        LOGGER.warning("Lock " + lockKey + " not acquired in " + timeout + " s, continuing with a local lock");
        return local.getLock(lockKey);
    }

    private void release(IMap<String, Object> map, String lockKey) {
        try {
            map.unlock(lockKey);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not release lock " + lockKey + ", it expires within " + timeout + " s", e);
        }
    }
}
