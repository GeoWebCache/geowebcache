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
package org.geowebcache.seed;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Represents a requests to truncate multiple gridsets or layers at once
 *
 * @author Kevin Smith, OpenGeo
 */
public interface MassTruncateRequest {

    /**
     * Perform the requested truncation
     *
     * @param sb The storage broker managing the cache
     * @param breeder The tile breeder storing information about the affected layers
     * @return {@literal true} if successful, {@literal false} otherwise
     */
    public boolean doTruncate(StorageBroker sb, TileBreeder breeder) throws StorageException, GeoWebCacheException;

    public default ResponseEntity<String> getResponse(String contentType) {
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
