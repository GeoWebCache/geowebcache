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
 * An in memory lock provider.
 *
 * <p>This provider does not constrain the number of locks that can be held at any given time. Because any one thread
 * can hold multiple locks at a time, a more appropriate approach to constraining resource usage would be to limit the
 * number of concurrent threads instead.
 *
 * <p>One objective of this class is to <a href="https://github.com/GeoWebCache/geowebcache/issues/1226">support nested
 * locking scenarios</a>. This class used to use a striped lock algorithm which would cause deadlocks for nested locking
 * because of the non-predictable manner in which any lock can be arbitrarily locked by another unrelated lock. An
 * example use case of nested locks, in pseudocode, would be:
 *
 * <pre>
 *  lock(metatile);
 *  try {
 *      for(tile : metatile.getTiles()){
 *          lock(tile);
 *          try{
 *              ... do work
 *           } finally {
 *               release(tile);
 *          }
 *      }
 *  } finally {
 *      release(metatile);
 *  }
 * </pre>
 *
 * @author Andrea Aime - GeoSolutions
 */
public class MemoryLockProvider implements LockProvider {

    private static final Logger LOGGER = Logging.getLogger(MemoryLockProvider.class.getName());

    ConcurrentHashMap<String, LockAndCounter> lockAndCounters = new ConcurrentHashMap<>();

    @Override
    public Lock getLock(String lockKey) {
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Acquiring lock key " + lockKey);

        // Atomically create a new LockAndCounter, or increment the existing one
        LockAndCounter lockAndCounter = lockAndCounters.compute(lockKey, (key, internalLockAndCounter) -> {
            if (internalLockAndCounter == null) {
                internalLockAndCounter = new LockAndCounter();
            }
            internalLockAndCounter.counter.incrementAndGet();
            return internalLockAndCounter;
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

                        // Try to remove the lock, but we have to check the count AGAIN inside of
                        // "compute"
                        // so that we know it hasn't been incremented since the if-statement above
                        // was evaluated
                        lockAndCounters.compute(lockKey, (key, existingLockAndCounter) -> {
                            if (existingLockAndCounter == null || existingLockAndCounter.counter.get() == 0) {
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

    /**
     * A ReentrantLock with a counter to track how many threads are waiting on this lock so we know if it's safe to
     * remove it during a release.
     */
    private static class LockAndCounter {
        private final java.util.concurrent.locks.Lock lock = new ReentrantLock();

        // The count of threads holding or waiting for this lock
        private final AtomicInteger counter = new AtomicInteger(0);
    }
}
