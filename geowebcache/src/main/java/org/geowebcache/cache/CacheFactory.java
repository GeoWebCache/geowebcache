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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CacheFactory {
	private static Log log = LogFactory.getLog(org.geowebcache.cache.CacheFactory.class);

	public static Cache getCache(String cachetype, Properties props) throws CacheException {
		Cache cache = null;
		if(log.isTraceEnabled()) {
			log.trace("Using cache class: " + cachetype);
		}
		try {
			Class cache_class = Class.forName(cachetype);
			cache = (Cache) cache_class.newInstance();
			cache.init(props);
			
		} catch(ClassNotFoundException cnf) {
			log.fatal("Could not get cache class: " + cachetype, cnf);
		} catch(IllegalAccessException iae) {
			log.fatal("Could not access cache class: " + cachetype, iae);
		} catch(InstantiationException ie) {
			log.fatal("Could not create instance of cache class: " + cachetype, ie);
		}

		if(log.isTraceEnabled()) {
			log.trace("Created cache: " + cache.getClass());
		}
		return cache;
	}
}
