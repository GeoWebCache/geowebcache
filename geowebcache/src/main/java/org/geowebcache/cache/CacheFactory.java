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
 * 
 * @author Arne Kepp, The Open Planning Project 2008
 *  
 */
package org.geowebcache.cache;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;

public class CacheFactory implements ApplicationContextAware {

    private static Log log = LogFactory
            .getLog(org.geowebcache.cache.CacheFactory.class);

    private WebApplicationContext context = null;

    private HashMap<String,Cache> caches = null;

    private String defaultCacheBeanId = null;

    private CacheKeyFactory cacheKeyFactory = null;

    public CacheFactory(CacheKeyFactory cacheKeyFactory) {
        this.cacheKeyFactory = cacheKeyFactory;
    }

    /**
     * Scans the classes for the specified bean and returns it, or null if it is
     * not found.
     * 
     * @param cacheBeanId
     *            the name, as defined in the Spring XML
     * @return singleton of the appropriate cache type, null otherwise
     */
    public Cache getCache(String cacheBeanId) {
        if (caches == null) {
            loadCaches();
        }
        Cache cache = (Cache) caches.get(cacheBeanId);
        if (cache == null) {
            log.error("Did not find cache for bean id " + cacheBeanId);
        } else {
            log.debug("Returning cache for " + cacheBeanId);
        }
        return cache;
    }

    /**
     * Get the default cache, based on the default bean set in Spring's XML
     * configuration
     * 
     * @return default cache
     */
    public Cache getDefaultCache() {
        if(caches == null || caches.size() > 1) {
            log.warn("Received request for default cache, returning " + defaultCacheBeanId);
            return getCache(defaultCacheBeanId);
        } else {
            // Oh so ugly...
            return caches.entrySet().iterator().next().getValue();
        }
    }

    /**
     * Uses Spring to scan classpath for classes that implement the interface
     * Cache.
     */
    private void loadCaches() {
        Map<String,Cache> cacheBeans = context.getBeansOfType(Cache.class);
        Iterator<Entry<String,Cache>> beanIter = cacheBeans.entrySet().iterator();
        
        caches = new HashMap<String,Cache>();
        
        while(beanIter.hasNext()) {
            Entry<String,Cache> entry = beanIter.next();
            caches.put(entry.getKey(), entry.getValue());
            log.debug("Added cache bean for " + entry.getValue().getClass().toString());
        }
    }
    
    /** 
     * This should only be used during testing
     */
    public void setCaches(HashMap<String, Cache> cacheMap) {
        caches = cacheMap;
    }

    public void setDefaultCacheBeanId(String defaultCacheBeanId) {
        this.defaultCacheBeanId = defaultCacheBeanId;
    }

    public CacheKeyFactory getCacheKeyFactory() {
        return this.cacheKeyFactory;
    }

    public void setApplicationContext(ApplicationContext arg0)
            throws BeansException {
        context = (WebApplicationContext) arg0;
    }
    
    public WebApplicationContext getWebAppContext() {
        return context;
    }
}
