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
 * Copyright OpenPlans 2008-2014
 * Copyright OSGeo 2014
 * 
 * @author Arne Kepp, The Open Planning Project
 */
package org.geowebcache.mime;

import org.geowebcache.GeoWebCacheException;

public class MimeException extends GeoWebCacheException {

    /**
     * 
     */
    private static final long serialVersionUID = 5435573041559578453L;

    public MimeException(String msg) {
        super(msg);
    }

}
