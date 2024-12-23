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
 * @author David Blasby, GeoCat, Copyright 2022
 */
package org.geowebcache.layer;

/**
 * Filter interface for the TileLayerDispatcher. This allows layers to be excluded from the list of available layers.
 */
public interface TileLayerDispatcherFilter {

    /**
     * Determine if the layer should be excluded (filtered out).
     *
     * @param tileLayer
     * @return true if this tileLayer should be excluded (filtered out)
     */
    boolean exclude(TileLayer tileLayer);
}
