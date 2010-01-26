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
 * 
 * @author Gabriel Roldan, OpenGeo, Copyright 2010
 */
package org.geowebcache.georss;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.replay;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.classextension.EasyMock;
import org.geowebcache.georss.DefaultSeedAreaProvider;
import org.geowebcache.georss.SeedAreaProvider;
import org.geowebcache.georss.SeedTask2;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.layer.wms.WMSMetaTile;
import org.geowebcache.layer.wms.WMSSourceHelper;
import org.geowebcache.rest.GWCTask.TYPE;
import org.geowebcache.rest.seed.SeedRequest;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileObject;
import org.geowebcache.util.TestUtils;

import com.vividsolutions.jts.geom.Coordinate;

/**
 * Unit test suite for {@link SeedTask2}
 * 
 * @author Gabriel Roldan (OpenGeo)
 * @version $Id$
 */
public class SeedTask2Test extends TestCase {

    private WMSLayer tl;

    private byte[] fakeWMSResponse;

    private WMSSourceHelper mockSourceHelper;

    private StorageBroker mockStorageBroker;

    protected void setUp() throws Exception {
        final GridSetBroker gridSetBroker = new GridSetBroker(false, false);

        tl = TestUtils.createWMSLayer("image/png", new GridSetBroker(false, false));

        // create an image to be returned by the mock WMSSourceHelper
        fakeWMSResponse = TestUtils.createFakeSourceImage(tl, gridSetBroker.WORLD_EPSG4326
                .getName());

        mockSourceHelper = EasyMock.createMock(WMSSourceHelper.class);

        tl.setSourceHelper(mockSourceHelper);

        mockStorageBroker = EasyMock.createMock(StorageBroker.class);
    }

    /**
     * For a metatiled seed request over a given zoom level, make sure the correct wms calls are
     * issued
     * 
     * @throws Exception
     */
    @SuppressWarnings("serial")
    public void testSeedWMSRequests() throws Exception {
        final AtomicInteger wmsRequestsCounter = new AtomicInteger();
        Capture<WMSMetaTile> wmsRequestsCapturer = new Capture<WMSMetaTile>() {
            /**
             * Override because setValue with anyTimes() resets the list of values
             */
            @Override
            public void setValue(WMSMetaTile o) {
                wmsRequestsCounter.incrementAndGet();
            }
        };
        expect(mockSourceHelper.makeRequest(capture(wmsRequestsCapturer))).andReturn(
                fakeWMSResponse).anyTimes();
        replay(mockSourceHelper);

        tl.setSourceHelper(mockSourceHelper);

        final int zoomLevel = 4;
        SeedRequest req = TestUtils.createSeedRequest(tl, TYPE.SEED, zoomLevel, zoomLevel);

        /*
         * Create a mock storage broker that does nothing
         */
        expect(mockStorageBroker.put((TileObject) anyObject())).andReturn(true).anyTimes();
        expect(mockStorageBroker.get((TileObject) anyObject())).andReturn(false).anyTimes();
        replay(mockStorageBroker);

        boolean reseed = false;
        SeedAreaProvider seedAreadProvider = new DefaultSeedAreaProvider(req, tl);
        SeedTask2 seedTask = new SeedTask2(mockStorageBroker, seedAreadProvider, tl, reseed, null, true);
        seedTask.setTaskId(1L);
        seedTask.setThreadInfo(1, 0);
        /*
         * HACK: avoid SeedTask.getCurrentThreadArrayIndex failure.
         */
        Thread.currentThread().setName("pool-fake-thread-1");

        /*
         * Call the seed process
         */
        seedTask.doAction();

        final long expectedWmsRequestsCount = 3; // due to metatiling
        final long wmsRequestCount = wmsRequestsCounter.get();
        assertEquals(expectedWmsRequestsCount, wmsRequestCount);
    }

    /**
     * Make sure when seeding a given zoom level, the correct tiles are sent to the
     * {@link StorageBroker}
     * 
     * @throws Exception
     */
    @SuppressWarnings("serial")
    public void testSeedStoredTiles() throws Exception {
        // WMSSourceHelper that on makeRequest() returns always the saqme fake image
        expect(mockSourceHelper.makeRequest((WMSMetaTile) anyObject())).andReturn(fakeWMSResponse)
                .anyTimes();
        replay(mockSourceHelper);
        
        final String gridSetId = tl.getGridSubsets().keySet().iterator().next();
        final int zoomLevel = 2;
        SeedRequest req = TestUtils.createSeedRequest(tl, TYPE.SEED, zoomLevel, zoomLevel);

        /*
         * Create a mock storage broker that has never an image in its blob store and that captures
         * the TileObject the seeder requests it to store for further test validation
         */
        Capture<TileObject> storedObjects = new Capture<TileObject>() {
            /**
             * Override because setValue with anyTimes() resets the list of values
             */
            @Override
            public void setValue(TileObject o) {
                super.getValues().add(o);
            }
        };
        expect(mockStorageBroker.put(capture(storedObjects))).andReturn(true).anyTimes();
        expect(mockStorageBroker.get((TileObject) anyObject())).andReturn(false).anyTimes();
        replay(mockStorageBroker);

        boolean reseed = false;
        SeedAreaProvider seedAreadProvider = new DefaultSeedAreaProvider(req, tl);
        SeedTask2 seedTask = new SeedTask2(mockStorageBroker, seedAreadProvider, tl, reseed, null, true);
        seedTask.setTaskId(1L);
        seedTask.setThreadInfo(1, 0);
        /*
         * HACK: avoid SeedTask.getCurrentThreadArrayIndex failure.
         */
        Thread.currentThread().setName("pool-fake-thread-1");

        /*
         * Call the seed process
         */
        seedTask.doAction();

        final GridSubset gridSubset = tl.getGridSubset(gridSetId);

        /*
         * Make sure the seed process asked for the expected tiles to be stored
         */
        final long expectedSavedTileCount;

        final long[] coveredGridLevels = gridSubset.getCoverage(zoomLevel);
        final int[] metaTilingFactors = tl.getMetaTilingFactors();

        // Round down to the closes meta-tile boundary before starting
        long starty = coveredGridLevels[1] - (coveredGridLevels[1] % metaTilingFactors[1]);
        // Round down to the closest meta-tile boundary before starting
        long startx = coveredGridLevels[0] - (coveredGridLevels[0] % metaTilingFactors[0]);

        expectedSavedTileCount = (coveredGridLevels[2] - startx + 1)
                * (coveredGridLevels[3] - starty + 1);

        List<TileObject> storedTiles = storedObjects.getValues();
        final int seededTileCount = storedTiles.size();

        assertEquals(expectedSavedTileCount, seededTileCount);

        // abuse the jts Coordinate construct as tile keys to assert the correct ones were stored
        Set<Coordinate> tileKeys = new TreeSet<Coordinate>();
        Set<Coordinate> expectedTiles = new TreeSet<Coordinate>();
        for (long x = startx; x <= coveredGridLevels[2]; x++) {
            for (long y = starty; y <= coveredGridLevels[3]; y++) {
                expectedTiles.add(new Coordinate(x, y, zoomLevel));
            }
        }
        for (TileObject obj : storedTiles) {
            tileKeys.add(new Coordinate(obj.getXYZ()[0], obj.getXYZ()[1], obj.getXYZ()[2]));
        }

        assertEquals(expectedTiles, tileKeys);
    }
}
