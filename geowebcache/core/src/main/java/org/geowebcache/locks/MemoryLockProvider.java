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

import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;

/**
 * An in memory lock provider based on a striped lock
 *
 * @author Andrea Aime - GeoSolutions
 */
public class MemoryLockProvider implements LockProvider {

    private static Logger LOGGER = Logging.getLogger(MemoryLockProvider.class.getName());

    java.util.concurrent.locks.Lock[] locks;

    public MemoryLockProvider() {
        this(1024);
    }

    public MemoryLockProvider(int concurrency) {
        locks = new java.util.concurrent.locks.Lock[concurrency];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public Lock getLock(String lockKey) {
        final int idx = getIndex(lockKey);
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("Mapped lock key " + lockKey + " to index " + idx + ". Acquiring lock.");
        locks[idx].lock();
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("Mapped lock key " + lockKey + " to index " + idx + ". Lock acquired");

        return new Lock() {

            boolean released = false;

            public void release() throws GeoWebCacheException {
                if (!released) {
                    released = true;
                    locks[idx].unlock();
                    if (LOGGER.isLoggable(Level.FINE))
                        LOGGER.fine("Released lock key " + lockKey + " mapped to index " + idx);
                }
            }
        };
    }

    private int getIndex(String lockKey) {
        // Simply hashing the lock key generated a significant number of collisions,
        // doing the SHA1 digest of it provides a much better distribution
        int idx = Math.abs(DigestUtils.sha1Hex(lockKey).hashCode() % locks.length);
        return idx;
    }
}
