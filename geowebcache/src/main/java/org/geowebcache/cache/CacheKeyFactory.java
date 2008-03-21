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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CacheKeyFactory {
    private static Log log = LogFactory
            .getLog(org.geowebcache.cache.CacheKeyFactory.class);

    public static CacheKey getCacheKey(String cachekeytype, String prefix) {
        CacheKey cachekey = null;
        if (log.isTraceEnabled()) {
            log.trace("Using cache class: " + cachekeytype);
        }
        try {
            Class cache_key_class = Class.forName(cachekeytype);
            cachekey = (CacheKey) cache_key_class.newInstance();
            cachekey.init(prefix);

        } catch (ClassNotFoundException cnf) {
            log.fatal("Could not get cache key class: " + cachekeytype, cnf);
        } catch (IllegalAccessException iae) {
            log.fatal("Could not access cache key class: " + cachekeytype, iae);
        } catch (InstantiationException ie) {
            log.fatal("Could not create instance of cache key class: "
                    + cachekeytype, ie);
        }

        if (log.isTraceEnabled()) {
            log.trace("Created cache key: " + cachekey.getClass());
        }
        return cachekey;
    }
}
