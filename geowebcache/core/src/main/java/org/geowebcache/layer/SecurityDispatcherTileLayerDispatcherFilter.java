/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author David Blasby, GeoCat, Copyright 2022
 */
package org.geowebcache.layer;

import org.geowebcache.filter.security.SecurityDispatcher;

public class SecurityDispatcherTileLayerDispatcherFilter implements TileLayerDispatcherFilter {

    SecurityDispatcher securityDispatcher;

    public SecurityDispatcherTileLayerDispatcherFilter(SecurityDispatcher securityDispatcher) {
        this.securityDispatcher = securityDispatcher;
    }

    /**
     * This uses the GWC SecurityDispatcher#checkSecurity to determine if the user has access to the
     * layer.
     *
     * @param tileLayer
     * @return true if the user doesn't have access to layer
     */
    @Override
    public boolean exclude(TileLayer tileLayer) {
        if (securityDispatcher == null) {
            return false;
        }
        try {
            securityDispatcher.checkSecurity(tileLayer, null, null);
        } catch (Exception e) {
            return true; // threw exception - we don't have access
        }
        return false;
    }
}
