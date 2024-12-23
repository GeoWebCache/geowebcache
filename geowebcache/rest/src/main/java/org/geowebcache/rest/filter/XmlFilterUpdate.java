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
 * @author David Vick, Boundless, Copyright 2017
 */
package org.geowebcache.rest.filter;

import java.io.IOException;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.exception.RestException;

public abstract class XmlFilterUpdate {
    public abstract void runUpdate(RequestFilter filter, TileLayer tl) throws IOException, RestException;
}
