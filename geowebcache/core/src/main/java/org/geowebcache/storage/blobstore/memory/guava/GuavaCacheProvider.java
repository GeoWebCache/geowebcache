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
package org.geowebcache.storage.blobstore.memory.guava;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.cache.Weigher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration.EvictionPolicy;
import org.geowebcache.storage.blobstore.memory.CacheProvider;
import org.geowebcache.storage.blobstore.memory.CacheStatistics;
import org.geowebcache.util.SuppressFBWarnings;

/**
 * This class is an implementation of the {@link CacheProvider} interface using a backing Guava
 * {@link Cache} object. This implementation requires to be configured with the configure() method.
 *
 * @author Nicola Lagomarsini Geosolutions
 */
public class GuavaCacheProvider implements CacheProvider {

    /** {@link Logger} object used for logging exceptions */
    private static final Logger LOGGER = Logging.getLogger(GuavaCacheProvider.class.getName());

    /** Separator char used for creating Cache keys */
    public static final String SEPARATOR = "_";

    /** Constant for multiplying bytes to MB */
    public static final long BYTES_TO_MB = 1048576;

    /** Size of the scheduled thread pool */
    public static final int CORE_POOL_SIZE = 1;

    private static final String GUAVA_NAME = "Guava Cache";

    /** Array containing the supported Policies */
    public static final List<EvictionPolicy> POLICIES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            EvictionPolicy.NULL,
                            EvictionPolicy.EXPIRE_AFTER_ACCESS,
                            EvictionPolicy.EXPIRE_AFTER_WRITE));

    /**
     * This class handles the {@link CacheStats} object returned by the guava cache.
     *
     * @author Nicola Lagomarsini Geosolutions
     */
    public static class GuavaCacheStatistics extends CacheStatistics {

        /** serialVersionUID */
        private static final long serialVersionUID = 1L;

        public GuavaCacheStatistics(
                CacheStats stats, double currentSpace, long actualSize, long totalSize) {
            this.setEvictionCount(stats.evictionCount());
            this.setHitCount(stats.hitCount());
            this.setMissCount(stats.missCount());
            this.setTotalCount(stats.requestCount());
            this.setHitRate((int) (stats.hitRate() * 100));
            this.setMissRate(100 - getHitRate());
            this.setCurrentMemoryOccupation(currentSpace);
            this.setActualSize(actualSize);
            this.setTotalSize(totalSize);
        }
    }

    /** Cache object containing the various {@link TileObject}s */
    private Cache<String, TileObject> cache;

    /** Internal Multimap used for storing the TileObject ids associated to each cached Layer */
    private LayerMap multimap;

    /** {@link AtomicBoolean} used for ensuring that the Cache has already been configured */
    private AtomicBoolean configured;

    /**
     * {@link AtomicLong} used for checking the number of active operations to wait when resetting
     * the cache
     */
    private AtomicLong actualOperations;

    /** Internal concurrent Set used for saving the names of the Layers that must not be cached */
    private final Set<String> layers;

    /** Cache total memory in Mb */
    private long maxMemory = 0L;

    /** {@link AtomicLong} used for storing the current cache size */
    private AtomicLong currentSize = new AtomicLong(0);

    private ScheduledExecutorService scheduledPool;

    public GuavaCacheProvider(CacheConfiguration config) {
        // Initialization of the Layer set and of the Atomic parameters
        layers = Collections.newSetFromMap(new ConcurrentHashMap<>());
        configured = new AtomicBoolean(false);
        actualOperations = new AtomicLong(0);
        configure(config);
    }

    /** This method is used for creating a new cache object, from the defined configuration. */
    private void initCache(CacheConfiguration configuration) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Building new Cache");
        }
        // Initialization step
        int concurrency = configuration.getConcurrencyLevel();
        maxMemory = configuration.getHardMemoryLimit() * BYTES_TO_MB;
        long evictionTime = configuration.getEvictionTime();
        EvictionPolicy policy = configuration.getPolicy();

        // If Cache already exists, flush it
        if (cache != null) {
            cache.invalidateAll();
        }
        // Create the CacheBuilder
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
        // Add weigher
        Weigher<String, TileObject> weigher =
                (key, value) -> {
                    currentSize.addAndGet(value.getBlobSize());
                    return value.getBlobSize();
                };
        // Create the builder
        CacheBuilder<String, TileObject> newBuilder =
                builder.maximumWeight(maxMemory)
                        .recordStats()
                        .weigher(weigher)
                        .concurrencyLevel(concurrency)
                        .removalListener(
                                notification -> {
                                    // TODO This operation is not atomic
                                    TileObject obj = notification.getValue();
                                    // Update the current size
                                    currentSize.addAndGet(-obj.getBlobSize());
                                    final String tileKey = generateTileKey(obj);
                                    final String layerName = obj.getLayerName();
                                    multimap.removeTile(layerName, tileKey);
                                    if (LOGGER.isLoggable(Level.FINE)) {
                                        LOGGER.fine(
                                                "Removed tile "
                                                        + tileKey
                                                        + " for layer "
                                                        + layerName
                                                        + " due to reason:"
                                                        + notification.getCause().toString());
                                        LOGGER.fine(
                                                "Removed tile was evicted? "
                                                        + notification.wasEvicted());
                                    }
                                });
        // Handle eviction policy
        boolean configuredPolicy = false;
        if (policy != null && evictionTime > 0) {
            if (policy == EvictionPolicy.EXPIRE_AFTER_ACCESS) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Configuring Expire After Access eviction policy");
                }
                newBuilder.expireAfterAccess(evictionTime, TimeUnit.SECONDS);
                configuredPolicy = true;
            } else if (policy == EvictionPolicy.EXPIRE_AFTER_WRITE) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Configuring Expire After Write eviction policy");
                }
                newBuilder.expireAfterWrite(evictionTime, TimeUnit.SECONDS);
                configuredPolicy = true;
            }
        }

        // Build the cache
        cache = newBuilder.build();

        // Created a new multimap
        multimap = new LayerMap();

        // Configure a new scheduling task if needed
        if (configuredPolicy) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Configuring Scheduled Task for cache eviction");
            }
            Runnable command =
                    () -> {
                        if (configured.get()) {
                            // Increment the number of current operations
                            // This behavior is used in order to wait
                            // the end of all the operations after setting
                            // the configured parameter to false
                            actualOperations.incrementAndGet();
                            try {
                                cache.cleanUp();
                            } finally {
                                // Decrement the number of current operations.
                                actualOperations.decrementAndGet();
                            }
                        }
                    };
            // Initialization of the internal Scheduler task for scheduling cache cleanup
            scheduledPool = Executors.newScheduledThreadPool(CORE_POOL_SIZE);
            scheduledPool.scheduleAtFixedRate(command, 10, evictionTime + 1, TimeUnit.SECONDS);
        }

        // Update the configured parameter
        configured.getAndSet(true);
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public synchronized void configure(CacheConfiguration configuration) {
        // NOTE that if the cache has already been configured, the user must always call
        // resetCache() before
        // setting the new configuration
        reset();
        // Configure a new cache
        initCache(configuration);
    }

    @Override
    public TileObject getTileObj(TileObject obj) {
        // Check if the cache has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Checking if the layer must not be cached");
                }
                // Check if the layer must be cached
                if (layers.contains(obj.getLayerName())) {
                    // The layer must not be cached
                    return null;
                }
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Retrieving TileObject: " + obj + " from cache");
                }
                // Generate the TileObject key
                String id = generateTileKey(obj);
                // Get the key from the cache
                return cache.getIfPresent(id);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return null;
    }

    @Override
    public void putTileObj(TileObject obj) {
        // Check if the cache has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Checking if the layer must not be cached");
                }
                // Check if the layer must be cached
                if (layers.contains(obj.getLayerName())) {
                    // The layer must not be cached
                    return;
                }
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Adding TileObject: " + obj + " to cache");
                }
                // Generate the TileObject key
                String id = generateTileKey(obj);
                // Add the TileObject to the cache and its id in the multimap
                cache.put(id, obj);
                multimap.putTile(obj.getLayerName(), id);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public void removeTileObj(TileObject obj) {
        // Check if the cache has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Checking if the layer must not be cached");
                }
                // Check if the layer must be cached
                if (layers.contains(obj.getLayerName())) {
                    // The layer must not be cached
                    return;
                }
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Removing TileObject: " + obj + " from cache");
                }
                // Generate the TileObject key
                String id = generateTileKey(obj);
                // Remove the key
                cache.invalidate(id);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public void removeLayer(String layername) {
        // Check if the cache has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Checking if the layer must not be cached");
                }
                // Check if the layer must be cached
                if (layers.contains(layername)) {
                    // The layer must not be cached
                    return;
                }
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Removing Layer: " + layername + " from cache");
                }
                // Get all the TileObject ids associated to the Layer and removes them
                Set<String> keys = multimap.removeLayer(layername);
                if (keys != null) {
                    cache.invalidateAll(keys);
                }
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public void clear() {
        // Check if the cache has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Flushing cache");
                }
                // Remove all the elements from the cache
                if (cache != null) {
                    cache.invalidateAll();
                }
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    @SuppressWarnings("PMD.EmptyControlStatement")
    public void reset() {
        if (configured.getAndSet(false)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Reset Cache internally");
            }
            // Avoid to call the While cycle before having started an operation with configured ==
            // false
            actualOperations.incrementAndGet();
            actualOperations.decrementAndGet();
            // Wait until all the operations are finished
            while (actualOperations.get() > 0) {}
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Flushing cache");
            }
            // Remove all the elements from the cache
            if (cache != null) {
                cache.invalidateAll();
            }
            // Remove all the Layers configured for avoiding caching
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Removing Layers");
            }
            layers.clear();
            // Shutdown the current Executor service
            if (scheduledPool != null) {
                scheduledPool.shutdown();
                try {
                    scheduledPool.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                    }
                    Thread.currentThread().interrupt();
                } finally {
                    scheduledPool = null;
                }
            }
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Cache is already reset");
            }
        }
    }

    @Override
    public CacheStatistics getStatistics() {
        // Check if the cache has already been configured
        if (configured.get()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Retrieving statistics");
            }
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Get cache statistics
                long actualSize = currentSize.get();
                long currentSpace =
                        (long)
                                (100L
                                        - (1L)
                                                * (100
                                                        * ((1.0d) * (maxMemory - actualSize))
                                                        / maxMemory));
                if (currentSpace < 0) {
                    currentSpace = 0;
                }
                // Returns a new Object containing a snapshot of the cache statistics
                return new GuavaCacheStatistics(cache.stats(), currentSpace, actualSize, maxMemory);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Returning empty statistics");
            }
            // Else returns an empty CacheStatistics object
            return new CacheStatistics();
        }
    }

    /**
     * * Static method for generating the {@link TileObject} cache key to use for caching.
     *
     * @return {@link TileObject} key
     */
    public static String generateTileKey(TileObject obj) {
        Map<String, String> parameters = obj.getParameters();

        StringBuilder builder =
                new StringBuilder(obj.getLayerName())
                        .append(SEPARATOR)
                        .append(obj.getGridSetId())
                        .append(SEPARATOR)
                        .append(Arrays.toString(obj.getXYZ()))
                        .append(SEPARATOR)
                        .append(obj.getBlobFormat());

        // If parameters are present they must be handled
        if (parameters != null && !parameters.isEmpty()) {
            for (String key : parameters.keySet()) {
                builder.append(SEPARATOR).append(key).append(SEPARATOR).append(parameters.get(key));
            }
        }

        return builder.toString();
    }

    @Override
    public String getName() {
        return GUAVA_NAME;
    }

    @Override
    public void addUncachedLayer(String layername) {
        // Check if the cache has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Adding Layer:" + layername + " to avoid cache");
                }
                // Adds the layer which should not be cached
                layers.add(layername);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public void removeUncachedLayer(String layername) {
        // Check if the cache has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Removing Layer:" + layername + " to avoid cache");
                }
                // Configure a Layer for being cached again
                layers.remove(layername);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public boolean containsUncachedLayer(String layername) {
        // Check if the cache has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Checking if Layer:" + layername + " must not be cached");
                }
                // Check if the layer must not be cached
                return layers.contains(layername);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        } else {
            return false;
        }
    }

    @Override
    public List<EvictionPolicy> getSupportedPolicies() {
        return POLICIES;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Internal class representing a concurrent multimap which associates to each Layer name the
     * related {@link TileObject} cache keys. This map is useful when trying to remove a Layer,
     * because it returns quicly all the cached keys of the selected layer, without having to cycle
     * on the cache and checking if each TileObject belongs to the selected Layer.
     *
     * @author Nicola Lagomarsini, GeoSolutions
     */
    static class LayerMap {

        /** {@link ReentrantReadWriteLock} used for handling concurrency */
        private final ReentrantReadWriteLock lock;

        /** {@link WriteLock} used when trying to change the map */
        private final WriteLock writeLock;

        /** {@link ReadLock} used when accessing the map */
        private final ReadLock readLock;

        /** MultiMap containing the {@link TileObject} keys for the Layers */
        private final ConcurrentHashMap<String, Set<String>> layerMap = new ConcurrentHashMap<>();

        public LayerMap() {
            // Lock initialization
            lock = new ReentrantReadWriteLock(true);
            writeLock = lock.writeLock();
            readLock = lock.readLock();
        }

        /** Insertion of a {@link TileObject} key in the map for the associated Layer. */
        // not sure why locking is used on top of concurrent structures to start with... just
        // ignoring to move on, but imho all locks should be removed and concurrent structure
        // be used as intended (e.g., putIfAbsent and the like
        @SuppressFBWarnings({
            "AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION",
            "UL_UNRELEASED_LOCK",
            "UL_UNRELEASED_LOCK_EXCEPTION_PATH"
        })
        public void putTile(String layer, String id) {
            // ReadLock is used because we are only accessing the map
            readLock.lock();
            Set<String> tileKeys = layerMap.get(layer);
            if (tileKeys == null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("No KeySet for Layer: " + layer);
                }
                // If the Map is not present, we must add it
                // So we do the unlock and try to acquire the writeLock
                readLock.unlock();
                writeLock.lock();
                try {
                    // Check again if the tileKey has not been added already
                    tileKeys = layerMap.get(layer);
                    if (tileKeys == null) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine("Creating new KeySet for Layer: " + layer);
                        }
                        // If no key is present then a new KeySet is created and then added to the
                        // multimap
                        tileKeys = new ConcurrentSkipListSet<>();
                        layerMap.put(layer, tileKeys);
                    }
                    // Downgrade by acquiring read lock before releasing write lock
                    readLock.lock();
                } finally {
                    // Release the writeLock
                    writeLock.unlock();
                }
            }
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Add the TileObject id to the Map");
                }
                // Finally the tile key is added.
                tileKeys.add(id);
            } finally {
                readLock.unlock();
            }
        }

        /** Removal of a {@link TileObject} key in the map for the associated Layer. */
        public void removeTile(String layer, String id) {
            // ReadLock is used because we are only accessing the map
            readLock.lock();
            try {
                // KeySet associated to the image
                Set<String> tileKeys = layerMap.get(layer);
                if (tileKeys != null) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("Remove TileObject id to the Map");
                    }
                    // Removal of the keys
                    tileKeys.remove(id);
                    // If the KeySet is empty then it is removed from the multimap
                    if (tileKeys.isEmpty()) {
                        readLock.unlock();
                        writeLock.lock();
                        try {
                            if (tileKeys.isEmpty()) {
                                // Here writeLock is acquired again, but it is reentrant
                                removeLayer(layer);
                            }
                            // Downgrade by acquiring read lock before releasing write lock
                            readLock.lock();
                        } finally {
                            writeLock.unlock();
                        }
                    }
                }
            } finally {
                readLock.unlock();
            }
        }

        /**
         * Removes a layer {@link Set} and returns it to the cache.
         *
         * @return the keys associated to the Layer
         */
        public Set<String> removeLayer(String layer) {
            writeLock.lock();
            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Removing KeySet for Layer: " + layer);
                }
                // Get the Set from the map
                Set<String> layers = layerMap.get(layer);
                // Removes the set from the map
                layerMap.remove(layer);
                // Returns the set
                return layers;
            } finally {
                writeLock.unlock();
            }
        }
    }
}
