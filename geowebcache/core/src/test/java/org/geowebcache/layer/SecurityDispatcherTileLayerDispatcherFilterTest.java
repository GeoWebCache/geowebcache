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
 * <p>Copyright 2022
 */
package org.geowebcache.layer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.junit.Test;
import org.mockito.Mockito;

public class SecurityDispatcherTileLayerDispatcherFilterTest {

    /**
     * Tests that the SecurityDispatcherTileLayerDispatcherFilter passes the request off to the
     * SecurityDispatcher. If the SecurityDispatcher#checkSecurity() doesn't throw, then that
     * TileLayer is NOT filtered ("false").
     */
    @Test
    public void testFilterIn() throws GeoWebCacheException {
        SecurityDispatcher securityDispatcher = Mockito.mock(SecurityDispatcher.class);

        SecurityDispatcherTileLayerDispatcherFilter filter =
                new SecurityDispatcherTileLayerDispatcherFilter(securityDispatcher);
        TileLayer tileLayer = Mockito.mock(TileLayer.class);
        // doesn't throw -> filter.exclude() will be false
        Mockito.doNothing().when(securityDispatcher).checkSecurity(tileLayer, null, null);

        boolean result = filter.exclude(tileLayer);

        assertFalse(result);
        Mockito.verify(securityDispatcher).checkSecurity(tileLayer, null, null);
    }

    /**
     * Tests that the SecurityDispatcherTileLayerDispatcherFilter passes the request off to the
     * SecurityDispatcher. If the SecurityDispatcher#checkSecurity() throws, then that TileLayer is
     * filtered out ("true").
     */
    @Test
    public void testFilterOut() throws GeoWebCacheException {
        SecurityDispatcher securityDispatcher = Mockito.mock(SecurityDispatcher.class);

        SecurityDispatcherTileLayerDispatcherFilter filter =
                new SecurityDispatcherTileLayerDispatcherFilter(securityDispatcher);
        TileLayer tileLayer = Mockito.mock(TileLayer.class);
        // throws -> filter.exclude() will be true
        Mockito.doThrow(new GeoWebCacheException(""))
                .when(securityDispatcher)
                .checkSecurity(tileLayer, null, null);

        boolean result = filter.exclude(tileLayer);

        assertTrue(result);
        Mockito.verify(securityDispatcher).checkSecurity(tileLayer, null, null);
    }
}
