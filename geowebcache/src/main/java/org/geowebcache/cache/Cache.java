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

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.tile.Tile;
import org.springframework.context.ApplicationContextAware;

public interface Cache extends ApplicationContextAware {
	public void setDefaultKeyBeanId(String defaultKeyBeanId);
	
    public void init(Properties props) throws CacheException;

    public void destroy();

    public void setUp(String cachePrefix) throws CacheException;

    public String getDefaultKeyBeanId();
    
    public String getDefaultPrefix(String param) throws CacheException;

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
    public boolean get(CacheKey keyProto, Tile tile, long ttl) 
    throws CacheException, GeoWebCacheException;

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
    public void set(CacheKey keyProto, Tile tile, long ttl) 
    throws CacheException, GeoWebCacheException;

    public void truncate(TileLayer tl, SRS srs, int zoomStart, int zoomStop, 
            int[][] bounds, MimeType mimeType) throws CacheException;
    
    public boolean remove(CacheKey keyProto, Tile tile) throws CacheException;

    public void removeAll() throws CacheException;

}
