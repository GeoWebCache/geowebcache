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
 * <p>Copyright 2026
 */
package org.geowebcache.diskquota;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageBroker;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DiskQuotaMonitorTest {

    private static final String LAYER = "layer1";

    @Rule
    public TemporaryFolder cacheRoot = new TemporaryFolder();

    private DiskQuotaMonitor monitor;

    @Before
    public void createCache() throws Exception {
        File zoomDir = new File(cacheRoot.getRoot(), LAYER + "/EPSG_4326_00/0_0");
        assertTrue(zoomDir.mkdirs());
        assertTrue(new File(zoomDir, "00_00.png").createNewFile());
    }

    @After
    public void shutDownMonitor() throws Exception {
        if (monitor != null && monitor.isRunning()) {
            monitor.shutDown(1);
        }
    }

    /** A store that does not record usage gets no traversal, even for a layer reporting zero usage. */
    @Test
    public void testSkipTraversalWhenStoreDoesNotTrackUsage() throws Exception {
        // no expectation for getTileLayer(): the monitor calls it right before traversing a layer
        TileLayerDispatcher tld = createMock(TileLayerDispatcher.class);
        expectLayerListing(tld);
        replay(tld);

        monitor = createMonitor(createStore(false), tld);
        monitor.startUp();

        verify(tld);
    }

    /** A store that records usage and has none saved for a layer gets that layer traversed. */
    @Test
    public void testTraverseLayerWithoutSavedUsage() throws Exception {
        TileLayer layer = createNiceMock(TileLayer.class);
        expect(layer.getName()).andReturn(LAYER).anyTimes();
        replay(layer);

        TileLayerDispatcher tld = createMock(TileLayerDispatcher.class);
        expectLayerListing(tld);
        expect(tld.getTileLayer(LAYER)).andReturn(layer).once();
        replay(tld);

        monitor = createMonitor(createStore(true), tld);
        monitor.startUp();

        verify(tld);
    }

    private static void expectLayerListing(TileLayerDispatcher tld) {
        expect(tld.getLayerNames()).andReturn(Collections.singleton(LAYER)).anyTimes();
        expect(tld.getLayerList()).andReturn(Collections.emptyList()).anyTimes();
        expect(tld.getLayerCount()).andReturn(1).anyTimes();
    }

    /** Creates a store reporting zero usage for every layer, as an empty or discarding store does. */
    private static QuotaStore createStore(boolean tracksUsage) throws Exception {
        TilePageCalculator calculator = createNiceMock(TilePageCalculator.class);
        replay(calculator);

        QuotaStore store = createNiceMock(QuotaStore.class);
        expect(store.tracksUsage()).andReturn(tracksUsage).anyTimes();
        expect(store.getUsedQuotaByLayerName(LAYER)).andReturn(new Quota()).anyTimes();
        expect(store.getTilePageCalculator()).andReturn(calculator).anyTimes();
        replay(store);
        return store;
    }

    private DiskQuotaMonitor createMonitor(QuotaStore store, TileLayerDispatcher tld) throws Exception {
        DefaultStorageFinder storageFinder = createNiceMock(DefaultStorageFinder.class);
        expect(storageFinder.getDefaultPath())
                .andReturn(cacheRoot.getRoot().getAbsolutePath())
                .anyTimes();

        DiskQuotaConfig config = new DiskQuotaConfig();
        config.setDefaults();
        ConfigLoader configLoader = createNiceMock(ConfigLoader.class);
        expect(configLoader.loadConfig()).andReturn(config).anyTimes();

        QuotaStoreProvider provider = createNiceMock(QuotaStoreProvider.class);
        expect(provider.getQuotaStore()).andReturn(store).anyTimes();

        StorageBroker broker = createNiceMock(StorageBroker.class);
        CacheCleaner cleaner = createNiceMock(CacheCleaner.class);
        replay(storageFinder, configLoader, provider, broker, cleaner);

        return new DiskQuotaMonitor(storageFinder, configLoader, tld, broker, provider, cleaner);
    }
}
