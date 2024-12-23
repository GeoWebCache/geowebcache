/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.locks;

import org.geowebcache.GeoWebCacheException;

/**
 * A no-op implementation of LockProvider. It does not actually lock anything, can be used to test if the other
 * subsystems continue to work properly in face of absence of locks
 *
 * @author Andrea Aime - GeoSolutions
 */
public class NoOpLockProvider implements LockProvider {

    @Override
    public LockProvider.Lock getLock(String lockKey) throws GeoWebCacheException {
        return () -> {
            // nothing to do
        };
    }
}
