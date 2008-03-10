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
package org.geowebcache.cache.jcs;

import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.jcs.JCS;
import org.apache.jcs.engine.behavior.IElementAttributes;

import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;


public class JCSCache implements Cache {

	private static Log log = LogFactory.getLog(org.geowebcache.cache.jcs.JCSCache.class);

	private static final String REGION_NAME = "geowebcache";

	private JCS jcscache;

	// Make this class a singleton to allow Cache interface methods to be public
	// while preventing multiple instances
	private static JCSCache singleton_inst = null;

	public JCSCache() throws InstantiationException {
		if(singleton_inst != null) {
			throw new InstantiationException("Only one JCSCache instance allowed!");
		} else {
			singleton_inst = this;
		}
	}

	public static JCSCache getInstance() {
		return singleton_inst;
	}
	
	
	public void init(Properties props) {
		// nothing to do
	}

	public void destroy() {
		jcscache.dispose();
		jcscache = null;
		// Force a garbage collection
		System.gc();
	}
	
	public void setUp() throws org.geowebcache.cache.CacheException {
		if(jcscache == null) {
			try {
				jcscache = JCS.getInstance(REGION_NAME);
			} catch (org.apache.jcs.access.exception.CacheException jcse) {
				log.error("Could not initialize JCS cache: ", jcse);
				throw new org.geowebcache.cache.CacheException(jcse);
			}
		}
	}

	
	public Object get(Object key, long ttl) throws org.geowebcache.cache.CacheException {
		Object rtn;
		try {
			rtn = jcscache.get(key);
		} catch(NullPointerException npe) {
			log.error("Cache not setup: ", npe);
			throw new CacheException(npe);
		}
		return rtn;
	}

	public boolean remove(Object key) throws org.geowebcache.cache.CacheException {
		try {
			jcscache.remove(key);
		} catch (org.apache.jcs.access.exception.CacheException jcse) {
			log.error("Could not remove object from JCS cache: ", jcse);
			throw new org.geowebcache.cache.CacheException(jcse);
		} catch(NullPointerException npe) {
			log.error("Cache not setup: ", npe);
			throw new CacheException(npe);
		}
		return true;
	}

	public void removeAll() throws org.geowebcache.cache.CacheException {
		try {
			jcscache.clear();
		} catch (org.apache.jcs.access.exception.CacheException jcse) {
			log.error("Could not clear the JCS cache: ", jcse);
			throw new org.geowebcache.cache.CacheException(jcse);
		} catch(NullPointerException npe) {
			log.error("Cache not setup: ", npe);
			throw new CacheException(npe);
		}
	}

	public void set(Object key, Object obj, long ttl) throws org.geowebcache.cache.CacheException {
		try {
			if(ttl > 0) {
				IElementAttributes ea = jcscache.getDefaultElementAttributes();
				ea.setMaxLifeSeconds(ttl / 1000);
				jcscache.put(key, obj, ea);
			} else {
				jcscache.put(key, obj);
			}
		} catch (org.apache.jcs.access.exception.CacheException jcse) {
			log.error("Could not put object into JCS cache: ", jcse);
			throw new org.geowebcache.cache.CacheException(jcse);
		} catch(NullPointerException npe) {
			log.error("Cache not setup: ", npe);
			throw new CacheException(npe);
		}
	}

	public String getDefaultCacheKeyName() {
		return "org.geowebache.cachekey.JCSKey";
	}

}
