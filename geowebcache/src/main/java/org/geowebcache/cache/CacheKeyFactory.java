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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class CacheKeyFactory implements ApplicationContextAware {
    private static Log log = LogFactory
            .getLog(org.geowebcache.cache.CacheKeyFactory.class);

    
    private ApplicationContext context = null;
    
    private HashMap cacheKeys = null;
    
    public CacheKeyFactory() {
    	
    }
    
    public CacheKey getCacheKey(String cacheKeyBeanId) {
        if(cacheKeys == null) {
        	loadCacheKeys();
        }
    	return (CacheKey) cacheKeys.get(cacheKeyBeanId);
    }
        
    private void loadCacheKeys() {
        Map cacheBeans = context.getBeansOfType(CacheKey.class);
        Iterator beanIter = cacheBeans.keySet().iterator();
        
        cacheKeys = new HashMap();
        while(beanIter.hasNext()) {
        	String beanId = (String) beanIter.next();
            CacheKey aCacheKey = (CacheKey) cacheBeans.get(beanId);
            cacheKeys.put(beanId, aCacheKey);
        }
    }
    
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		context = arg0;
	}
}
