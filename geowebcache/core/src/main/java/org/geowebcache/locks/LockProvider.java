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
 */
package org.geowebcache.locks;

import org.geowebcache.GeoWebCacheException;

/**
 * Generic abstraction for getting/releasing exclusive locks
 * 
 * @author Andrea Aime - GeoSolutions
 */
public interface LockProvider {
    
    /**
     * Acquires a exclusive lock on the specified key
     * @param lockKey
     */
    public Lock getLock(String lockKey) throws GeoWebCacheException;
    
    
    public interface Lock {
        /**
         * Releases the lock on the specified key
         * @param lockKey
         */
        public void release() throws GeoWebCacheException;
    }

}
