package org.geowebcache.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.wms.WMSLayer;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

/**
 * Class for handling layers with EHCache
 * 
 * @author ez
 * @see Cache
 * @see ICache
 */
public class EhCacheManager {

    private static Log log = LogFactory.getLog(org.geowebcache.config.EhCacheManager.class);

    private CacheManager manager;

    private Cache layerCache;

    private enum CACHES {

        LAYERS("layers");

        // name used in ehcache.xml
        public final String cacheName;

        private CACHES(final String cacheName) {
            this.cacheName = cacheName;
        }

    }

    public EhCacheManager() {
        manager = CacheManager.create();

        // init all caches
        layerCache = initCache(CACHES.LAYERS);

        log.info("Initialized layer cache");
    }

    public void clearAll() {
        log.info("Clearing all caches..");

        logInfo(manager.getCacheNames());
        manager.clearAll();
        logInfo(manager.getCacheNames());
    }

    public void clear(String cacheName) throws CacheException {
        if (!manager.cacheExists(cacheName)) {
            throw new CacheException("No cache found: name='" + cacheName + "'");
        }

        Cache cache = manager.getCache(cacheName);
        logInfo(cache);
        cache.removeAll();
        logInfo(cache);
    }

    public void addLayer(WMSLayer layer) {
        if (layer != null) {
            if (layerCache.getMemoryStoreSize() == layerCache.getCacheConfiguration()
                    .getMaxElementsInMemory()) {
                log.warn(String.format(
                        "The limit for cache '%s' is exceeded, it is highly recommended that the limit is increased",
                        layerCache.getName()));
            }

            log.info("Adding layer to cache: " + layer.getName());

            put(layerCache, new Element(layer.getName(), layer));
        }
    }

    public WMSLayer getLayer(String layerName) {
        if (layerName == null) {
            return null;
        }

        Element e = layerCache.get(layerName);

        return e == null ? null : (WMSLayer) e.getObjectValue();
    }

    public void removeLayer(String layerName) {
        if (layerName != null) {
            layerCache.remove(layerName);
        }
    }

    /**
     * Checks if cache exists, if not creates it
     * 
     * @param name
     */
    private Cache initCache(CACHES c) {
        String cacheName = c.cacheName;

        if (!manager.cacheExists(cacheName)) {
            manager.addCache(cacheName);
            log.info("New cache added to manager: name=" + cacheName);
        }

        Cache cache = manager.getCache(cacheName);
        logInfo(cache);

        return cache;
    }

    private void put(Cache cache, Element el) {
        int limit = cache.getCacheConfiguration().getMaxElementsInMemory();
        // if limit is reached log it
        if (cache.getMemoryStoreSize() == limit) {
            log.warn(String.format(
                    "The limit for maxElementsInMemory for cache '%s' is exceeded, it is highly recommended that the limit is increased: limit=%d",
                    cache.getName(), limit));
        }

        cache.put(el);
    }

    private void logInfo(Cache cache) {
        if (cache != null) {
            // cache.getCacheConfiguration().getMaxElementsInMemory()
            log.info("Cache info: name= " + cache.getName() + " count=" + cache.getSize()
                    + " objects=" + cache.getStatistics().getObjectCount() + " getMemoryStoreSize="
                    + cache.getMemoryStoreSize() + " maxElementsInCache="
                    + cache.getCacheConfiguration().getMaxElementsInMemory());
        }
    }

    private void logInfo(String... names) {
        for (String name : names) {
            logInfo(manager.getCache(name));
        }
    }

}
