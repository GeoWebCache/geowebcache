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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;

import static org.geowebcache.TestHelpers.createWMSLayer;
import static org.geowebcache.TestHelpers.createFakeSourceImage;
import static org.geowebcache.TestHelpers.createRequest;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.classextension.EasyMock;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileObject;
import org.geowebcache.util.MockWMSSourceHelper;

import com.mockrunner.mock.web.MockHttpServletRequest;
import com.mockrunner.mock.web.MockHttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.easymock.IAnswer;
import org.geowebcache.grid.*;
import org.geowebcache.io.Resource;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.*;

/**
 * Unit test suite for {@link WMSLayer}
 * 
 * @author Gabriel Roldan (OpenGeo)
 * @version $Id$
 */
public class WMSLayerTest extends TestCase {

    private final GridSetBroker gridSetBroker = new GridSetBroker(false, false);

    public void testSeedMetaTiled() throws Exception {
        WMSLayer layer = createWMSLayer("image/png");

        WMSSourceHelper mockSourceHelper = new MockWMSSourceHelper();

        layer.setSourceHelper(mockSourceHelper);

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
    }
    
    public void testMinMaxCache() throws Exception {
        WMSLayer tl = createWMSLayer("image/png",5,6);

        // create an image to be returned by the mock WMSSourceHelper
        final byte[] fakeWMSResponse = createFakeSourceImage(tl);

        // WMSSourceHelper that on makeRequest() returns always the same fake image
        WMSSourceHelper mockSourceHelper = EasyMock.createMock(WMSSourceHelper.class);

        final AtomicInteger wmsRequestsCounter = new AtomicInteger();
        Capture<WMSMetaTile> wmsRequestsCapturer = new Capture<WMSMetaTile>() {
            @Override
            public void setValue(WMSMetaTile o) {
                wmsRequestsCounter.incrementAndGet();
            }
        };
        Capture<Resource> resourceCapturer = new Capture<Resource>() {
            @Override
            public void setValue(Resource target) {
                try {
                    target.transferFrom(Channels.newChannel(new ByteArrayInputStream(
                            fakeWMSResponse)));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        mockSourceHelper.makeRequest(capture(wmsRequestsCapturer), capture(resourceCapturer));
        expectLastCall().anyTimes().asStub();
        replay(mockSourceHelper);
        
        tl.setSourceHelper(mockSourceHelper);

        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        final AtomicInteger cacheHits = new AtomicInteger();
        final AtomicInteger cacheMisses = new AtomicInteger();
        final TransientCache transientCache = new TransientCache(100, 100);
        expect(mockStorageBroker.getTransient( (TileObject) anyObject())).andAnswer(new IAnswer<Boolean>() {
            public Boolean answer() throws Throwable {
                TileObject tile = (TileObject) EasyMock.getCurrentArguments()[0];
                String key = StorageBroker.computeTransientKey(tile);
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
        
        mockStorageBroker.putTransient( capture(new Capture<TileObject>() {
            @Override
            public void setValue(TileObject tile) {
                String key = StorageBroker.computeTransientKey(tile);
                synchronized (transientCache) {
                    transientCache.put(key, tile.getBlob());
                }
            }
        }));
        expectLastCall().anyTimes();
        
        expect(mockStorageBroker.put((TileObject) anyObject())).andReturn(true).anyTimes();
        expect(mockStorageBroker.get((TileObject) anyObject())).andReturn(false).anyTimes();
        replay(mockStorageBroker);

        // we're not really seeding, just using the range
        SeedRequest req = createRequest(tl, GWCTask.TYPE.SEED, 4, 7);
        TileRange tr = TileBreeder.createTileRange(req, tl);
        
        getTiles(mockStorageBroker, tr, tl);

        // @todo test pass criteria needs to be formalized!
        final long wmsRequestCount = wmsRequestsCounter.get();
        System.out.println("cacheSize " + transientCache.size());
        System.out.println("cacheStorage " + transientCache.storageSize());
        System.out.println("cacheHits " + cacheHits.get());
        System.out.println("cacheMisses " + cacheMisses.get());
        System.out.println("requests " + wmsRequestCount);
    }

    private void getTiles(StorageBroker storageBroker, TileRange tr, final WMSLayer tl) throws Exception {
        final String layerName = tl.getName();
        // define the meta tile size to 1,1 so we hit all the tiles
        final TileRangeIterator trIter = new TileRangeIterator(tr, new int[] {1,1});

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
        
        for (int i = 0; i < futures.size(); i++) {
            futures.get(i).get();
        }
        requests.shutdown();
        
    }

}
