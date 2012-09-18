package org.geowebcache.diskquota.storage;

public class PageStoreConfig {

    private Integer cacheMemoryPercentAllowed;

    private Integer cacheSizeMB;

    /**
     * Percentage of the JVM heap size that can be used for the store's memory cache
     * <p>
     * This value and {@link #getCacheSizeMB()} are mutually exclusive. If both are present this
     * value takes precedence over {@link #getCacheSizeMB()}
     * </p>
     * 
     * @return {@code null} if not set, an integer between 0 and 100 otherwise, representing the max
     *         percentage of JVM assigned heap size the store can use for its internal cache.
     */
    public Integer getCacheMemoryPercentAllowed() {
        return cacheMemoryPercentAllowed;
    }

    /**
     * Maximum size in MB that can be used for the store's memory cache
     * <p>
     * This value and {@link #getCacheMemoryPercentAllowed()} are mutually exclusive. If both are
     * present {@link #getCacheMemoryPercentAllowed()} value takes precedence over this value;
     * </p>
     * 
     * @return {@code null} if not set, an integer between 0 and 100 otherwise, representing the max
     *         percentage of JVM assigned heap size the store can use for its internal cache.
     */
    public Integer getCacheSizeMB() {
        return cacheSizeMB;
    }

}
