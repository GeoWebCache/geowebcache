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
 * <p>Copyright 2018
 */
package org.geowebcache.arcgis.layer;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.SimpleGridSetConfiguration;
import org.geowebcache.grid.GridSet;

/** {@link org.geowebcache.config.GridSetConfiguration} for ArcGIS cache generated gridsets */
public class ArcGISCacheGridsetConfiguration extends SimpleGridSetConfiguration {

    @Override
    public String getIdentifier() {
        return "ArcGIS Cache Generated Gridsets";
    }

    @Override
    public String getLocation() {
        return "";
    }

    @Override
    public void afterPropertiesSet() throws GeoWebCacheException {}

    @Override
    protected void addInternal(GridSet gs) {
        super.addInternal(gs);
    }
}
