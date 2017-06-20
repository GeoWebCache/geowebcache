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
 * @author Kevin Smith, Boundless, 2017
 */

package org.geowebcache;

import java.util.Objects;

public class UncheckedGeoWebCacheException extends RuntimeException {
    
    /** serialVersionUID */
    private static final long serialVersionUID = -7981050129260733945L;

    public UncheckedGeoWebCacheException(GeoWebCacheException cause) {
        super(cause);
        Objects.requireNonNull(cause);
    }
    
    @Override
    public synchronized GeoWebCacheException getCause() {
        return (GeoWebCacheException) super.getCause();
    }
}
