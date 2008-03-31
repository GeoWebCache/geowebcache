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

import java.util.Properties;

public interface Cache {
	public void setDefaultKeyBeanId(String defaultKeyBeanId);
	
    public void init(Properties props) throws CacheException;

    public void destroy();

    public void setUp(String cachePrefix) throws CacheException;

    public String getDefaultKeyBeanId();

    /**
     * 
     * Note that maxAge is only honored if the backend supports it. See static
     * value defined on GeoWebCache
     * 
     * @param key
     *            the cache key
     * @param ttl
     *            the maximum age of the object, in milliseconds
     * @return
     * @throws CacheException
     */
    public Object get(Object key, long ttl) throws CacheException;

    /**
     * 
     * Note that ttl is only honored if the backend supports it. See static
     * value defined on GeoWebCache
     * 
     * @param key
     * @param obj
     * @param ttl
     *            the maximum amount of time to cache the object, in
     *            milliseconds
     * @throws CacheException
     */
    public void set(Object key, Object obj, long ttl) throws CacheException;

    public boolean remove(Object key) throws CacheException;

    public void removeAll() throws CacheException;

}
