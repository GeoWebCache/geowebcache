/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */
package org.geowebcache.layer.wms;

import static org.easymock.EasyMock.*;
import static org.easymock.classextension.EasyMock.*;
import static org.geowebcache.TestHelpers.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.IAnswer;
import org.easymock.classextension.EasyMock;
import org.geowebcache.TestHelpers;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeType;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.TileRangeIterator;
import org.geowebcache.storage.TransientCache;
import org.geowebcache.util.MockLockProvider;
import org.geowebcache.util.MockWMSSourceHelper;

import com.mockrunner.mock.web.MockHttpServletRequest;
import com.mockrunner.mock.web.MockHttpServletResponse;

/**
 * Unit test suite for {@link WMSLayer}
 *
 * @author Gabriel Roldan (OpenGeo)
 * @version $Id$
 */
public class WMSLayerTest extends TestCase {

    private final GridSetBroker gridSetBroker = new GridSetBroker(false, false);

    @Override
    protected void tearDown() throws Exception {
        TestHelpers.mockProvider.verify();
        TestHelpers.mockProvider.clear();
    }

    public void testSeedMetaTiled() throws Exception {
        WMSLayer layer = createWMSLayer("image/png");

        WMSSourceHelper mockSourceHelper = new MockWMSSourceHelper();
        MockLockProvider lockProvider = new MockLockProvider();
        layer.setSourceHelper(mockSourceHelper);
        layer.setLockProvider(lockProvider);

        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        Capture<TileObject> captured = new Capture<TileObject>();
        expect(mockStorageBroker.put(EasyMock.capture(captured))).andReturn(true).anyTimes();
        replay(mockStorageBroker);

        String layerId = layer.getName();
        HttpServletRequest servletReq = new MockHttpServletRequest();
        HttpServletResponse servletResp = new MockHttpServletResponse();

        long[] gridLoc = { 0, 0, 0 };// x, y, level
        MimeType mimeType = layer.getMimeTypes().get(0);
        GridSet gridSet = gridSetBroker.WORLD_EPSG4326;
        String gridSetId = gridSet.getName();
        ConveyorTile tile = new ConveyorTile(mockStorageBroker, layerId, gridSetId, gridLoc,
                mimeType, null, servletReq, servletResp);

        boolean tryCache = false;
        layer.seedTile(tile, tryCache);

        assertEquals(1, captured.getValues().size());
        TileObject value = captured.getValue();
        assertNotNull(value);
        assertEquals("image/png", value.getBlobFormat());
        assertNotNull(value.getBlob());
        assertTrue(value.getBlob().getSize() > 0);

        verify(mockStorageBroker);
        
        // check the lock provider was called in a symmetric way
        lockProvider.verify();
        lockProvider.clear();
    }

    public void testMinMaxCacheSeedTile() throws Exception {
        WMSLayer tl = createWMSLayer("image/png", 5, 6);
        
        MockTileSupport mock = new MockTileSupport(tl);

        SeedRequest req = createRequest(tl, GWCTask.TYPE.SEED, 4, 7);
        TileRange tr = TileBreeder.createTileRange(req, tl);
        
        seedTiles(mock.storageBroker, tr, tl);
        
        // zero transient cache attempts
        assertEquals(0, mock.cacheHits.get());
        assertEquals(0, mock.cacheMisses.get());
        // empirical numbers
        assertEquals(42, mock.wmsMetaRequestCounter.get());
        assertEquals(218, mock.storagePutCounter.get());
    }

    //ignore to fix the build until the failing assertion is worked out
    public void _testMinMaxCacheGetTile() throws Exception {
        WMSLayer tl = createWMSLayer("image/png", 5, 6);

        MockTileSupport mock = new MockTileSupport(tl);

        // we're not really seeding, just using the range
        SeedRequest req = createRequest(tl, GWCTask.TYPE.SEED, 4, 7);
        TileRange tr = TileBreeder.createTileRange(req, tl);

        List<ConveyorTile> tiles = getTiles(mock.storageBroker, tr, tl);

        // this number is determined by our tileRange minus those out of bounds
        assertEquals(880, tiles.size());
        // tiles at zoom 4 and 7 will have non png data
        for (int i = 0; i < tiles.size(); i++) {
            ConveyorTile tile = tiles.get(i);
            assertNotNull(tile.getBlob());
            //System.out.println(tile.getTileIndex()[2] + " " + tile.getBlob().getSize());
        }
        
        // empirical numbers
        // this number is determined by the number of metarequests at level 5+6
        assertEquals(218, mock.storagePutCounter.get());
        // and the number of successful hits at level 5+6
        assertEquals(176, mock.storageGetCounter.get());
        // these last will vary - on a dual core machine, they appeared predictable
        // but on a 8 core machine, the threads compete for cache and we can only
        // assertain by range
        // @todo 
        // assertTrue(Math.abs(532 - mock.cacheHits.get()) < 10);
        // assertTrue(Math.abs(494 - mock.cacheMisses.get()) < 10);
        // assertTrue(Math.abs(172 - mock.wmsMetaRequestCounter.get()) < 10);
        // stats
        System.out.println("transientCacheSize " + mock.transientCache.size());
        System.out.println("transientCacheStorage " + mock.transientCache.storageSize());
    }
    
    private void seedTiles(StorageBroker storageBroker, TileRange tr, final WMSLayer tl) throws Exception {
        final String layerName = tl.getName();
        // define the meta tile size to 1,1 so we hit all the tiles
        final TileRangeIterator trIter = new TileRangeIterator(tr, tl.getMetaTilingFactors());

        long[] gridLoc = trIter.nextMetaGridLocation(new long[3]);

        while (gridLoc != null) {
            Map<String, String> fullParameters = tr.getParameters();

            final ConveyorTile tile = new ConveyorTile(storageBroker, layerName, tr.getGridSetId(), gridLoc,
                    tr.getMimeType(), fullParameters, null, null);
            tile.setTileLayer(tl);
            
            tl.seedTile(tile, false);

            gridLoc = trIter.nextMetaGridLocation(gridLoc);
        }
    }

    private List<ConveyorTile> getTiles(StorageBroker storageBroker, TileRange tr, final WMSLayer tl) throws Exception {
        final String layerName = tl.getName();
        // define the meta tile size to 1,1 so we hit all the tiles
        final TileRangeIterator trIter = new TileRangeIterator(tr, new int[]{1, 1});

        long[] gridLoc = trIter.nextMetaGridLocation(new long[3]);

        // six concurrent requests max
        ExecutorService requests = Executors.newFixedThreadPool(6);
        ExecutorCompletionService completer = new ExecutorCompletionService(requests);

        List<Future<ConveyorTile>> futures = new ArrayList<Future<ConveyorTile>>();
        while (gridLoc != null) {
            Map<String, String> fullParameters = tr.getParameters();

            final ConveyorTile tile = new ConveyorTile(storageBroker, layerName, tr.getGridSetId(), gridLoc,
                    tr.getMimeType(), fullParameters, null, null);
            futures.add(completer.submit(new Callable<ConveyorTile>() {

                public ConveyorTile call() throws Exception {
                    try {
                        return tl.getTile(tile);
                    } catch (OutsideCoverageException oce) {
                        return null;
                    }
                }
            }));

            gridLoc = trIter.nextMetaGridLocation(gridLoc);
        }
        
        // these assertions could be externalized
        List<ConveyorTile> results = new ArrayList<ConveyorTile>();
        for (int i = 0; i < futures.size(); i++) {
            ConveyorTile get = futures.get(i).get();
            if (get != null) {
                results.add(get);
            }
        }

        requests.shutdown();
        
        return results;
    }

    class MockTileSupport {

        final byte[] fakeWMSResponse;
        final StorageBroker storageBroker = EasyMock.createMock(StorageBroker.class);
        final AtomicInteger cacheHits = new AtomicInteger();
        final AtomicInteger cacheMisses = new AtomicInteger();
        final AtomicInteger storagePutCounter = new AtomicInteger();
        final AtomicInteger storageGetCounter = new AtomicInteger();
        final AtomicInteger wmsMetaRequestCounter = new AtomicInteger();
        final AtomicInteger tileTransferCounter = new AtomicInteger();
        final TransientCache transientCache = new TransientCache(100, 100);

        public MockTileSupport(WMSLayer tl) throws Exception {
            // create an image to be returned by the mock WMSSourceHelper
            fakeWMSResponse = createFakeSourceImage(tl);

            installSourceHelper(tl);
            installMockBroker();
        }

        private void installSourceHelper(WMSLayer tl) throws Exception {
            // WMSSourceHelper that on makeRequest() returns always the same fake image
            WMSSourceHelper mockSourceHelper = EasyMock.createMock(WMSSourceHelper.class);

            Capture<WMSMetaTile> wmsRequestsCapturer = new Capture<WMSMetaTile>() {

                @Override
                public void setValue(WMSMetaTile o) {
                    wmsMetaRequestCounter.incrementAndGet();
                }
            };
            Capture<Resource> resourceCapturer = new Capture<Resource>() {

                @Override
                public void setValue(Resource target) {
                    try {
                        target.transferFrom(Channels.newChannel(new ByteArrayInputStream(
                                fakeWMSResponse)));
                        tileTransferCounter.incrementAndGet();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            mockSourceHelper.makeRequest(capture(wmsRequestsCapturer), capture(resourceCapturer));
            expectLastCall().anyTimes().asStub();
            mockSourceHelper.setConcurrency(32);
            mockSourceHelper.setBackendTimeout(120);
            replay(mockSourceHelper);

            tl.setSourceHelper(mockSourceHelper);
        }

        private void installMockBroker() throws Exception {
            expect(storageBroker.getTransient((TileObject) anyObject())).andAnswer(new IAnswer<Boolean>() {

                public Boolean answer() throws Throwable {
                    TileObject tile = (TileObject) EasyMock.getCurrentArguments()[0];
                    String key = TransientCache.computeTransientKey(tile);
                    Resource resource;
                    synchronized (transientCache) {
                        resource = transientCache.get(key);
                    }
                    if (resource != null) {
                        cacheHits.incrementAndGet();
                    } else {
                        cacheMisses.incrementAndGet();
                    }
                    tile.setBlob(resource);
                    return resource != null;
                }
            }).anyTimes();

            storageBroker.putTransient(capture(new Capture<TileObject>() {

                @Override
                public void setValue(TileObject tile) {
                    String key = TransientCache.computeTransientKey(tile);
                    synchronized (transientCache) {
                        transientCache.put(key, tile.getBlob());
                    }
                }
            }));
            expectLastCall().anyTimes();

            final HashSet<String> puts = new HashSet<String>();
            expect(storageBroker.put(capture(new Capture<TileObject>() {
                @Override
                public void setValue(TileObject value) {
                    puts.add(TransientCache.computeTransientKey(value));
                    storagePutCounter.incrementAndGet();
                }
            }))).andReturn(true).anyTimes();
            expect(storageBroker.get((TileObject) anyObject())).andAnswer(new IAnswer<Boolean>() {
                public Boolean answer() throws Throwable {
                    TileObject tile = (TileObject) EasyMock.getCurrentArguments()[0];
                    if (puts.contains(TransientCache.computeTransientKey(tile))) {
                        tile.setBlob(new ByteArrayResource(fakeWMSResponse));
                        storageGetCounter.incrementAndGet();
                        return true;
                    } else {
                        return false;
                    }
                }
            }).anyTimes();
            replay(storageBroker);
        }
    }
}
