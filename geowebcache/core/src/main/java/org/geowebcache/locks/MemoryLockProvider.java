/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.geowebcache.locks;

import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.codec.digest.DigestUtils;
import org.geowebcache.GeoWebCacheException;

/**
 * An in memory lock provider based on a striped lock
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class MemoryLockProvider implements LockProvider {
    
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
        locks[idx].lock();        
        return new Lock() {
            
            boolean released = false;

            public void release() throws GeoWebCacheException {
                if(!released) {
                    released = true;
                    locks[idx].unlock();
                }
            }
            
        };
        
    }

    private int getIndex(String lockKey) {
        // Simply hashing the lock key generated a significant number of collisions,
        // doing the SHA1 digest of it provides a much better distribution
        int idx = Math.abs(DigestUtils.shaHex(lockKey).hashCode() % locks.length);
        return idx;
    }

}
