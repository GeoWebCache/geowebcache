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
 * @author Arne Kepp, The Open Planning Project, Copyright 2007
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

public class CacheKeyFactory implements ApplicationContextAware {
    private static Log log = LogFactory
            .getLog(org.geowebcache.cache.CacheKeyFactory.class);

    
    private ApplicationContext context = null;
    
    private Map<String,CacheKey> cacheKeys = null;
    
    public CacheKeyFactory() {
    	
    }
    
    public CacheKey getCacheKey(String cacheKeyBeanId) {
        if(cacheKeys == null) {
        	loadCacheKeys();
        }
    	return (CacheKey) cacheKeys.get(cacheKeyBeanId);
    }
        
    private void loadCacheKeys() {
        Map<String,CacheKey> cacheBeans = context.getBeansOfType(CacheKey.class);
        Iterator<Entry<String,CacheKey>> beanIter = cacheBeans.entrySet().iterator();
        
        cacheKeys = new HashMap<String,CacheKey>();
        
        while(beanIter.hasNext()) {
            Entry<String,CacheKey> entry = beanIter.next();
            cacheKeys.put(entry.getKey(), entry.getValue());
            log.debug("Added cache key bean for " + entry.getValue().getClass().toString());
        }
    }
    
    public void setCacheKeys(Map<String,CacheKey> cacheKeys) {
        this.cacheKeys = cacheKeys;
    }
    
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		context = arg0;
	}
}
