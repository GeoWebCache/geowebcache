package org.geowebcache.locks;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.codec.digest.DigestUtils;

public class MemoryLockProvider implements LockProvider {
    
    Lock[] locks;
    
    public MemoryLockProvider() {
        this(1024);
    }

    public MemoryLockProvider(int concurrency) {
        locks = new Lock[concurrency];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public void getLock(String lockKey) {
        int idx = getIndex(lockKey);
        locks[idx].lock();
    }

    private int getIndex(String lockKey) {
        int idx = Math.abs(DigestUtils.shaHex(lockKey).hashCode() % locks.length);
        return idx;
    }

    public void releaseLock(String lockKey) {
        int idx = getIndex(lockKey);
        locks[idx].unlock();
    }

}
