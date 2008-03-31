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
 * @author Chris Whitney
 *  
 */
package org.geowebcache.cache;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.service.Service;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class CacheFactory implements ApplicationContextAware {
    private static Log log = LogFactory
            .getLog(org.geowebcache.cache.CacheFactory.class);
    
    private ApplicationContext context = null;
    
    private HashMap caches = null;
    
    private String defaultCacheBeandId = null;
    
    private CacheKeyFactory cacheKeyFactory = null;
    
    public CacheFactory(CacheKeyFactory cacheKeyFactory) {
    	this.cacheKeyFactory = cacheKeyFactory;
    }
    
    public Cache getCache(String cacheBeanId) {
        if(caches == null) {
        	loadCaches();
        }
    	return (Cache) caches.get(cacheBeanId);
    }
    
    public Cache getDefaultCache() {
        if(caches == null) {
        	loadCaches();
        }
        return (Cache) caches.get(defaultCacheBeandId);
    }
    
    private void loadCaches() {
        Map cacheBeans = context.getBeansOfType(Cache.class);
        Iterator beanIter = cacheBeans.keySet().iterator();
        
        caches = new HashMap();
        while(beanIter.hasNext()) {
        	String beanId = (String) beanIter.next();
            Cache aCache = (Cache) cacheBeans.get(beanId);
            caches.put(beanId, aCache);
        }
    }
    
    public void setDefaultCacheBeanId(String defaultCacheBeanId) {
    	this.defaultCacheBeandId = defaultCacheBeanId;
    }
    
   public CacheKeyFactory getCacheKeyFactory() {
	   return this.cacheKeyFactory;
   }
    
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		context = arg0;
	}
}
