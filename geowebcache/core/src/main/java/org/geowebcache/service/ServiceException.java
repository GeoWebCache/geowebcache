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
package org.geowebcache.service;

import java.io.Serial;
import org.geowebcache.GeoWebCacheException;

public class ServiceException extends GeoWebCacheException {

    public ServiceException(Throwable thrw) {
        super(thrw);
    }

    public ServiceException(String message) {
        super(message);
    }

    @Serial
    private static final long serialVersionUID = 30867687291108387L;
}
