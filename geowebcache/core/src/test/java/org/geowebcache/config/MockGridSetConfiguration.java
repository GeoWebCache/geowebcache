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
package org.geowebcache.config;

import java.util.Arrays;
import java.util.List;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSet;

/** Mock Gridset configuration for testing */
public class MockGridSetConfiguration extends SimpleGridSetConfiguration {

    public MockGridSetConfiguration(GridSet... mocks) {
        super();
        for (GridSet g : mocks) {
            addInternal(g);
        }
    }

    @Override
    public void reinitialize() throws GeoWebCacheException {}

    @Override
    public String getIdentifier() {
        return "MockGridSetConfiguration";
    }

    @Override
    public String getLocation() {
        return "Mock";
    }

    /** List containing a DefaultGridsets and a MockGridSetConfiguration */
    public static List<GridSetConfiguration> withDefaults(
            boolean useEPSG900913, boolean useGWC11xNames, GridSet... mocks) {
        return Arrays.asList(new DefaultGridsets(useEPSG900913, useGWC11xNames), new MockGridSetConfiguration(mocks));
    }

    /** List containing a DefaultGridsets and a MockGridSetConfiguration */
    public static List<GridSetConfiguration> withDefaults(GridSet... mocks) {
        return Arrays.asList(new DefaultGridsets(true, true), new MockGridSetConfiguration(mocks));
    }

    @Override
    public void afterPropertiesSet() throws Exception {}
}
