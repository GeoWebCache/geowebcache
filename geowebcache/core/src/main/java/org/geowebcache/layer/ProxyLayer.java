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
 * @author Andrea Aime, GeoSolutions, Copyright 2014
 */
package org.geowebcache.layer;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;

/**
 * Implemented by layers that are able to cascade requests (such as the WMS ones) to the underlying back-end
 *
 * @author Andrea Aime - GeoSolutions
 */
public interface ProxyLayer {

    public void proxyRequest(ConveyorTile tile) throws GeoWebCacheException;
}
