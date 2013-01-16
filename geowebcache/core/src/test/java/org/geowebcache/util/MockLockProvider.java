package org.geowebcache.util;

import static junit.framework.Assert.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.MemoryLockProvider;

/**
 * Mock lock provider, used for tests
 * 
 * @author Andrea Aime - GeoSolutions
 *
 */
public class MockLockProvider extends MemoryLockProvider {
    
    public AtomicLong acquires = new AtomicLong();
    public AtomicLong releases = new AtomicLong();
    public Map<String, String> keys = new ConcurrentHashMap<String, String>();

    public Lock getLock(final String lockKey) {
        super.getLock(lockKey);
        acquires.incrementAndGet();
        assertFalse(keys.containsKey(lockKey));
        keys.put(lockKey, lockKey);
        return new Lock() {

            public void release() throws GeoWebCacheException {
                releases.incrementAndGet();
                assertTrue(keys.containsKey(lockKey));
                keys.remove(lockKey);
            }
            
        };
    }

    public void verify() {
        assertEquals(acquires.get(), releases.get());
        assertEquals(0, keys.size());
    }
    
    public void clear() {
        acquires.set(0);
        releases.set(0);
    }

}
