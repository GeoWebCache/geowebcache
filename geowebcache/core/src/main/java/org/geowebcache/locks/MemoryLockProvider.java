/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.locks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

/**
 * An in memory lock provider based on a striped lock
 *
 * @author Andrea Aime - GeoSolutions
 */
public class MemoryLockProvider implements LockProvider {

    private static Logger LOGGER = Logging.getLogger(MemoryLockProvider.class.getName());

    ConcurrentHashMap<String, LockAndCounter> lockAndCounters = new ConcurrentHashMap<>();

    @Override
    public Lock getLock(String lockKey) {
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Acquiring lock key " + lockKey);

        LockAndCounter lockAndCounter =
                lockAndCounters.compute(
                        lockKey,
                        (key, existingLockAndCounter) -> {
                            if (existingLockAndCounter == null) {
                                existingLockAndCounter = new LockAndCounter();
                            }
                            existingLockAndCounter.counter.incrementAndGet();
                            return existingLockAndCounter;
                        });

        lockAndCounter.lock.lock();

        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Acquired lock key " + lockKey);

        return new Lock() {

            boolean released = false;

            @Override
            public void release() {
                if (!released) {
                    released = true;

                    LockAndCounter lockAndCounter = lockAndCounters.get(lockKey);
                    lockAndCounter.lock.unlock();

                    // Attempt to remove lock if no other thread is waiting for it
                    if (lockAndCounter.counter.decrementAndGet() == 0) {

                        lockAndCounters.compute(
                                lockKey,
                                (key, existingLockAndCounter) -> {
                                    if (existingLockAndCounter == null
                                            || existingLockAndCounter.counter.get() == 0) {
                                        return null;
                                    }
                                    return existingLockAndCounter;
                                });
                    }

                    if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Released lock key " + lockKey);
                }
            }
        };
    }

    private static class LockAndCounter {
        private final java.util.concurrent.locks.Lock lock = new ReentrantLock();

        /**
         * Track how many threads are waiting on this lock so we know if it's safe to remove it
         * during a release.
         */
        private final AtomicInteger counter = new AtomicInteger(0);
    }
}
