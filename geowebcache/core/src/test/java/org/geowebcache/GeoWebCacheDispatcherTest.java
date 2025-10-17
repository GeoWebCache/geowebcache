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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache;

import static org.geowebcache.TestHelpers.hasStatus;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.easymock.MockType;
import org.geowebcache.config.ServerConfiguration;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.service.Service;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.BlobStoreAggregator;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageBroker;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class GeoWebCacheDispatcherTest {

    @Rule
    public MockExtensionRule extensions = new MockExtensionRule();

    @Test
    public void testHomePageUser() throws Exception {
        String html = doTestHomePage(false);
        assertThat(html, containsString("GWC Home"));
        assertThat(html, containsString("Welcome to GeoWebCache"));
        assertThat(html, not(containsString(" version ")));
        assertThat(html, not(containsString(" build ")));
        assertThat(html, not(containsString("Runtime Statistics")));
        assertThat(html, not(containsString("Storage Locations")));
    }

    @Test
    public void testHomePageAdmin() throws Exception {
        String html = doTestHomePage(true);
        assertThat(html, containsString("GWC Home"));
        assertThat(html, containsString("Welcome to GeoWebCache"));
        assertThat(html, containsString(" version "));
        assertThat(html, containsString(" build "));
        assertThat(html, containsString("Runtime Statistics"));
        assertThat(html, containsString("Storage Locations"));
    }

    private String doTestHomePage(boolean isAdmin) throws Exception {
        IMocksControl stubs = EasyMock.createControl(MockType.NICE);
        TileLayerDispatcher tld = stubs.createMock("tld", TileLayerDispatcher.class);
        GridSetBroker gsb = stubs.createMock("gsb", GridSetBroker.class);
        StorageBroker sb = stubs.createMock("sb", StorageBroker.class);
        BlobStoreAggregator bsa = stubs.createMock("bsa", BlobStoreAggregator.class);
        ServerConfiguration config = stubs.createMock("config", ServerConfiguration.class);
        RuntimeStats rts = stubs.createMock("rts", RuntimeStats.class);
        DefaultStorageFinder dfs = stubs.createMock("dfs", DefaultStorageFinder.class);
        SecurityDispatcher secDisp = stubs.createMock("secDisp", SecurityDispatcher.class);

        EasyMock.expect(secDisp.isAdmin()).andReturn(isAdmin);
        EasyMock.expect(config.isRuntimeStatsEnabled()).andStubReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/geowebcache/home");
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setContextPath("/geowebcache");

        stubs.replay();

        // Bean init
        GeoWebCacheDispatcher dispatcher = new GeoWebCacheDispatcher(tld, gsb, sb, bsa, config, rts);
        dispatcher.setApplicationContext(extensions.getMockContext());
        dispatcher.setDefaultStorageFinder(dfs);
        dispatcher.setSecurityDispatcher(secDisp);

        // The test
        dispatcher.handleRequest(request, response);
        assertThat(response, hasStatus(HttpStatus.OK));

        stubs.verify();
        return response.getContentAsString();
    }

    @Test
    public void testRequestNoSecurity() throws Exception {
        IMocksControl stubs = EasyMock.createControl(MockType.NICE);
        TileLayerDispatcher tld = stubs.createMock("tld", TileLayerDispatcher.class);
        GridSetBroker gsb = stubs.createMock("gsb", GridSetBroker.class);
        StorageBroker sb = stubs.createMock("sb", StorageBroker.class);
        BlobStoreAggregator bsa = stubs.createMock("bsa", BlobStoreAggregator.class);
        ServerConfiguration config = stubs.createMock("config", ServerConfiguration.class);
        RuntimeStats rts = stubs.createMock("rts", RuntimeStats.class);
        DefaultStorageFinder dfs = stubs.createMock("dfs", DefaultStorageFinder.class);
        TileLayer layer = EasyMock.createMock("layer", TileLayer.class);
        GridSubset subset = stubs.createMock("subset", GridSubset.class);
        SecurityDispatcher secDisp = stubs.createMock("secDisp", SecurityDispatcher.class);
        SRS srs = SRS.getEPSG3857();

        EasyMock.expect(config.isRuntimeStatsEnabled()).andStubReturn(false);

        Service testService = EasyMock.createMock("testService", Service.class);

        EasyMock.expect(testService.getPathName()).andStubReturn("testService");
        EasyMock.expect(tld.getTileLayer("testLayer")).andStubReturn(layer);
        EasyMock.expect(layer.isEnabled()).andStubReturn(true);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/geowebcache/service/testService/testRequest1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setContextPath("/geowebcache");

        ConveyorTile conv = new ConveyorTile(
                sb,
                "testLayer",
                "testGrid",
                new long[] {1, 2, 3},
                ImageMime.png,
                Collections.emptyMap(),
                request,
                response);

        layer.applyRequestFilters(conv);
        EasyMock.expectLastCall().anyTimes();
        EasyMock.expect(testService.getConveyor(EasyMock.eq(request), EasyMock.anyObject(HttpServletResponse.class)))
                .andReturn(conv);
        EasyMock.expect(layer.getTile(conv)).andReturn(conv).once();
        EasyMock.expect(layer.getGridSubset("testGrid")).andStubReturn(subset);
        EasyMock.expect(layer.useETags()).andStubReturn(false);
        EasyMock.expect(subset.boundsFromIndex(EasyMock.aryEq(new long[] {1, 2, 3})))
                .andStubReturn(new BoundingBox(10, 20, 30, 40));
        EasyMock.expect(subset.getName()).andStubReturn("testGrid");
        EasyMock.expect(subset.getSRS()).andStubReturn(srs);

        secDisp.checkSecurity(conv);
        EasyMock.expectLastCall().once();

        stubs.replay();
        EasyMock.replay(testService, layer);

        // Bean init
        extensions.addBean("testService", testService, Service.class);
        GeoWebCacheDispatcher dispatcher = new GeoWebCacheDispatcher(tld, gsb, sb, bsa, config, rts);
        dispatcher.setApplicationContext(extensions.getMockContext());
        dispatcher.setDefaultStorageFinder(dfs);
        dispatcher.setSecurityDispatcher(secDisp);

        // The test
        dispatcher.handleRequest(request, response);

        assertThat(response, hasStatus(HttpStatus.OK));

        stubs.verify();
        EasyMock.verify(testService, layer);
    }

    @Test
    public void testRequestFail() throws Exception {
        IMocksControl stubs = EasyMock.createControl(MockType.NICE);
        TileLayerDispatcher tld = stubs.createMock("tld", TileLayerDispatcher.class);
        GridSetBroker gsb = stubs.createMock("gsb", GridSetBroker.class);
        StorageBroker sb = stubs.createMock("sb", StorageBroker.class);
        BlobStoreAggregator bsa = stubs.createMock("bsa", BlobStoreAggregator.class);
        ServerConfiguration config = stubs.createMock("config", ServerConfiguration.class);
        RuntimeStats rts = stubs.createMock("rts", RuntimeStats.class);
        DefaultStorageFinder dfs = stubs.createMock("dfs", DefaultStorageFinder.class);
        TileLayer layer = EasyMock.createMock("layer", TileLayer.class);
        GridSubset subset = stubs.createMock("subset", GridSubset.class);
        SecurityDispatcher secDisp = stubs.createMock("secDisp", SecurityDispatcher.class);
        SRS srs = SRS.getEPSG3857();

        EasyMock.expect(config.isRuntimeStatsEnabled()).andStubReturn(false);

        Service testService = EasyMock.createMock("testService", Service.class);

        EasyMock.expect(testService.getPathName()).andStubReturn("testService");
        EasyMock.expect(tld.getTileLayer("testLayer")).andStubReturn(layer);
        EasyMock.expect(layer.isEnabled()).andStubReturn(true);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/geowebcache/service/testService/testRequest1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setContextPath("/geowebcache");

        ConveyorTile conv = new ConveyorTile(
                sb,
                "testLayer",
                "testGrid",
                new long[] {1, 2, 3},
                ImageMime.png,
                Collections.emptyMap(),
                request,
                response);

        layer.applyRequestFilters(conv);
        EasyMock.expectLastCall().anyTimes();
        EasyMock.expect(testService.getConveyor(EasyMock.eq(request), EasyMock.anyObject(HttpServletResponse.class)))
                .andReturn(conv);
        // EasyMock.expect(layer.getTile(conv)).andReturn(conv).once(); // Intentionally don't
        // expect this
        EasyMock.expect(layer.getGridSubset("testGrid")).andStubReturn(subset);
        EasyMock.expect(subset.boundsFromIndex(EasyMock.aryEq(new long[] {1, 2, 3})))
                .andStubReturn(new BoundingBox(10, 20, 30, 40));
        EasyMock.expect(subset.getName()).andStubReturn("testGrid");
        EasyMock.expect(subset.getSRS()).andStubReturn(srs);

        secDisp.checkSecurity(conv);
        EasyMock.expectLastCall().andThrow(new SecurityException("Unauthorized because TEST"));

        stubs.replay();
        EasyMock.replay(testService, layer);

        // Bean init
        extensions.addBean("testService", testService, Service.class);
        GeoWebCacheDispatcher dispatcher = new GeoWebCacheDispatcher(tld, gsb, sb, bsa, config, rts);
        dispatcher.setApplicationContext(extensions.getMockContext());
        dispatcher.setDefaultStorageFinder(dfs);
        dispatcher.setSecurityDispatcher(secDisp);

        // The test
        dispatcher.handleRequest(request, response);

        assertThat(response, hasStatus(HttpStatus.FORBIDDEN));

        stubs.verify();
        EasyMock.verify(testService, layer);
    }
}
