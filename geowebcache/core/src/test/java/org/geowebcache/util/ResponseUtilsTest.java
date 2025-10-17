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
 * <p>Copyright 2023
 */
package org.geowebcache.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.request.RequestFilterException;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.layer.EmptyTileException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ApplicationMime;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.DefaultStorageFinder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class ResponseUtilsTest {

    @Mock
    SecurityDispatcher sd;

    @Mock
    ConveyorTile tile;

    @Mock
    TileLayer tileLayer;

    @Mock
    TileLayerDispatcher tld;

    @Mock
    DefaultStorageFinder storage;

    @Mock
    RuntimeStats stats;

    private MockHttpServletResponse response;

    @Before
    public void setUp() throws Exception {
        when(tld.getTileLayer("layer")).thenReturn(tileLayer);
        this.response = new MockHttpServletResponse();
        tile.servletResp = response;
    }

    @Test
    public void writeEmptyTileNoContent() throws GeoWebCacheException, RequestFilterException, IOException {
        when(tileLayer.getTile(tile)).thenThrow(new EmptyTileException(ApplicationMime.mapboxVector));

        ResponseUtils.writeTile(sd, tile, "layer", tld, storage, stats);
        // empty response, thus 204 no content
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
        assertEquals(ApplicationMime.mapboxVector.getMimeType(), response.getContentType());
        assertEquals("No tile data available for this location", response.getHeader("geowebcache-message"));
    }

    @Test
    public void writeEmptyTileJSON() throws GeoWebCacheException, RequestFilterException, IOException {
        String emptyJSON = "{}";
        when(tileLayer.getTile(tile))
                .thenThrow(new EmptyTileException(
                        ApplicationMime.json, new ByteArrayResource(emptyJSON.getBytes(StandardCharsets.UTF_8))));

        ResponseUtils.writeTile(sd, tile, "layer", tld, storage, stats);
        // within coverage and with a non empty body, 200 OK is warranted
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertEquals(ApplicationMime.json.getMimeType(), response.getContentType());
        assertEquals(emptyJSON, response.getContentAsString());
        assertEquals("No tile data available for this location", response.getHeader("geowebcache-message"));
    }

    @Test
    public void writeOutOfBoundsTile() throws GeoWebCacheException, RequestFilterException, IOException {
        when(tileLayer.getTile(tile)).thenThrow(new OutsideCoverageException(new long[] {0, 0, 10}, 0, 5));

        ResponseUtils.writeTile(sd, tile, "layer", tld, storage, stats);
        // just checks the empty tile did not modify the current behavior, although I'm not liking
        // the 200 OK....
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertEquals("image/png", response.getContentType());
        assertEquals("Zoom level was 10, but value has to be in [0,5]", response.getHeader("geowebcache-message"));
    }
}
