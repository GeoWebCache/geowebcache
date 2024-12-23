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
 * <p>Copyright 2021
 */
package org.geowebcache.layer;

import org.geowebcache.layer.meta.TileJSON;

/** Interface to be implemented by layers which can provide a TileJSON document */
public interface TileJSONProvider {

    /* Returns true if TileJSON is supported or not compatible */
    boolean supportsTileJSON();

    /* Returns a TileJSON for this layer, or null if not supported or not compatible */
    TileJSON getTileJSON();
}
