package org.geowebcache.s3.callback;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.s3.statistics.BatchStats;
import org.geowebcache.s3.statistics.ResultStat;
import org.geowebcache.s3.statistics.Statistics;
import org.geowebcache.s3.statistics.SubStats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public class LockingDecorator implements Callback {
    private final Map<String, LockProvider.Lock> locksPrePrefix = new ConcurrentHashMap<>();
    private final Callback delegate;
    private final LockProvider lockProvider;
    private final Logger logger;

    private SubStats currentSubStats = null;

    public LockingDecorator(Callback delegate, LockProvider lockProvider, Logger logger) {
        checkNotNull(delegate, "delegate cannot be null");
        checkNotNull(lockProvider, "lockProvider cannot be null");
        checkNotNull(logger, "logger cannot be null");

        this.delegate = delegate;
        this.lockProvider = lockProvider;
        this.logger = logger;
    }

    public void addLock(String key) {
        try {
            synchronized (lockProvider){
                LockProvider.Lock lock = lockProvider.getLock(key);
                locksPrePrefix.putIfAbsent(key, lock);
            }
            logger.info(format("Locked %s", key));
        } catch (GeoWebCacheException ex) {
            logger.severe(format("Could not lock %s because %s", key, ex.getMessage()));
        }
    }

    public void removeLock(String key) {
        try {
            synchronized (lockProvider){
                LockProvider.Lock lock = locksPrePrefix.get(key);
                lock.release();
            }
            logger.info(format("Unlocked %s", key));
        } catch (GeoWebCacheException e) {
            logger.warning("Unable to release lock for key: " + key);
        }
    }

    @Override
    public void tileResult(ResultStat result) {
        delegate.tileResult(result);
    }

    @Override
    public void batchStarted(BatchStats stats) {
        delegate.batchStarted(stats);
    }

    @Override
    public void batchEnded() {
        delegate.batchEnded();
    }

    @Override
    public void subTaskStarted(SubStats subStats) {
        this.currentSubStats = subStats;
        String key = currentSubStats.getDeleteTileRange().path();
        addLock(key);
        delegate.subTaskStarted(subStats);
    }

    @Override
    public void subTaskEnded() {
        String key = currentSubStats.getDeleteTileRange().path();
        removeLock(key);
        delegate.subTaskEnded();
    }

    @Override
    public void taskStarted(Statistics statistics) {
        delegate.taskStarted(statistics);
    }

    @Override
    public void taskEnded() {
        try {
            delegate.taskEnded();
        } finally {
            // Remove any outstanding locks
            if (!locksPrePrefix.isEmpty()) {
                synchronized (lockProvider) {
                    locksPrePrefix.forEach((key, value) -> removeLock(key));
                }
            }
        }

    }
}
