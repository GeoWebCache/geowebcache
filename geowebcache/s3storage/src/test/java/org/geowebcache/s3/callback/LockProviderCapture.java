package org.geowebcache.s3.callback;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;

import java.util.List;

import static org.geowebcache.s3.callback.LockProviderCapture.LockProviderMode.*;

public class LockProviderCapture implements LockProvider {
    private final LockProviderMode lockProviderMode;

    private static final List<LockProviderMode> succeedOnLock = List.of(AlwaysSucceed, ThrowOnRelease);
    private static final List<LockProviderMode> succeedOnRelease = List.of(AlwaysSucceed, ThrowOnLock);

    long lockCount = 0;
    long unlockCount = 0;

    public LockProviderCapture(LockProviderMode lockProviderMode) {
        this.lockProviderMode = lockProviderMode;
    }

    public long getLockCount() {
        return lockCount;
    }

    public Long getUnlockCount() {
        return unlockCount;
    }

    @Override
    public Lock getLock(String lockKey) throws GeoWebCacheException {
        if (succeedOnLock.contains(lockProviderMode)) {
            lockCount++;
            return new CaptureLock(lockProviderMode, this);
        } else {
            throw new GeoWebCacheException("Failed to get a lock");
        }
    }

    public static class CaptureLock implements Lock {
        private final LockProviderMode lockProviderMode;
        private final LockProviderCapture lockProviderCapture;

        public CaptureLock(LockProviderMode lockProviderMode, LockProviderCapture lockProviderCapture) {
            this.lockProviderMode = lockProviderMode;
            this.lockProviderCapture = lockProviderCapture;
        }

        @Override
        public void release() throws GeoWebCacheException {
            if (!succeedOnRelease.contains(lockProviderMode)) {
                throw new GeoWebCacheException("Failed to release a lock");
            }
            lockProviderCapture.unlockCount++;
        }
    }

    public enum LockProviderMode {
        AlwaysSucceed,
        AlwaysFail,
        ThrowOnLock,
        ThrowOnRelease
    }
}
