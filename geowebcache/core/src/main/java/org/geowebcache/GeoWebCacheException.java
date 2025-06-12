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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache;

import java.io.Serial;

public class GeoWebCacheException extends Exception {
    /** */
    @Serial
    private static final long serialVersionUID = 5837933971679774371L;

    public GeoWebCacheException(String msg) {
        super(msg);
    }

    public GeoWebCacheException(Throwable thrw) {
        super(thrw);
    }

    public GeoWebCacheException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
