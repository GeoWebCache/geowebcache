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
 * @author Gabriel Roldan, OpenGeo, Copyright 2010
 */
package org.geowebcache.seed;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.geowebcache.TestHelpers.createFakeSourceImage;
import static org.geowebcache.TestHelpers.createRequest;
import static org.geowebcache.TestHelpers.createWMSLayer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.layer.wms.WMSMetaTile;
import org.geowebcache.layer.wms.WMSSourceHelper;
import org.geowebcache.mime.MimeType;
import org.geowebcache.seed.GWCTask.TYPE;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.TileRangeIterator;
import org.geowebcache.util.MockWMSSourceHelper;
import org.geowebcache.util.Sleeper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test suite for {@link SeedTask}
 *
 * @author Gabriel Roldan (OpenGeo)
 * @version $Id$
 */
public class SeedTaskTest {

    @Before
    public void setUp() throws Exception {}

    @After
    public void tearDown() throws Exception {}

    /**
     * For a metatiled seed request over a given zoom level, make sure the correct wms calls are
     * issued
     */
    @Test
    @SuppressWarnings("serial")
    public void testSeedWMSRequests() throws Exception {
        WMSLayer tl = createWMSLayer("image/png");

        // create an image to be returned by the mock WMSSourceHelper
        final byte[] fakeWMSResponse = createFakeSourceImage(tl);

        // WMSSourceHelper that on makeRequest() returns always the saqme fake image
        WMSSourceHelper mockSourceHelper = EasyMock.createMock(WMSSourceHelper.class);

        final AtomicInteger wmsRequestsCounter = new AtomicInteger();
        Capture<WMSMetaTile> wmsRequestsCapturer = EasyMock.newCapture();
        Capture<Resource> resourceCapturer = EasyMock.newCapture();

        IAnswer<Void> answer =
                new IAnswer<Void>() {
                    @Override
                    public Void answer() throws Throwable {
                        wmsRequestsCounter.incrementAndGet();
                        try {
                            resourceCapturer
                                    .getValue()
                                    .transferFrom(
                                            Channels.newChannel(
                                                    new ByteArrayInputStream(fakeWMSResponse)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return null;
                    }
                };
        mockSourceHelper.makeRequest(capture(wmsRequestsCapturer), capture(resourceCapturer));
        expectLastCall().andAnswer(answer).anyTimes();
        mockSourceHelper.makeRequest(capture(wmsRequestsCapturer), capture(resourceCapturer));
        mockSourceHelper.makeRequest(capture(wmsRequestsCapturer), capture(resourceCapturer));
        mockSourceHelper.setConcurrency(32);
        mockSourceHelper.setBackendTimeout(120);
        replay(mockSourceHelper);

        tl.setSourceHelper(mockSourceHelper);

        final int zoomLevel = 4;
        SeedRequest req = createRequest(tl, TYPE.SEED, zoomLevel, zoomLevel);

        TileRange tr = TileBreeder.createTileRange(req, tl);
        TileRangeIterator trIter = new TileRangeIterator(tr, tl.getMetaTilingFactors());

        /*
         * Create a mock storage broker that does nothing
         */
        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        expect(mockStorageBroker.put(anyObject())).andReturn(true).anyTimes();
        expect(mockStorageBroker.get(anyObject())).andReturn(false).anyTimes();
        replay(mockStorageBroker);

        boolean reseed = false;
        SeedTask seedTask = new SeedTask(mockStorageBroker, trIter, tl, reseed, false);
        seedTask.setTaskId(1L);
        seedTask.setThreadInfo(new AtomicInteger(), 0);
        Sleeper sleeper = createMock(Sleeper.class);
        // Should not be called
        replay(sleeper);
        seedTask.sleeper = sleeper;

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
        Assert.assertEquals(expectedWmsRequestsCount, wmsRequestCount);
        verify(sleeper);
    }

    /**
     * For a metatiled seed request over a given zoom level, make sure the correct wms calls are
     * issued
     */
    @Test
    public void testSeedRetries() throws Exception {
        WMSLayer tl = createWMSLayer("image/png");

        // create an image to be returned by the mock WMSSourceHelper
        final byte[] fakeWMSResponse = createFakeSourceImage(tl);

        // WMSSourceHelper that on makeRequest() returns always the saqme fake image
        // WMSSourceHelper mockSourceHelper = new MockWMSSourceHelper();///
        // EasyMock.createMock(WMSSourceHelper.class);
        WMSSourceHelper mockSourceHelper =
                new MockWMSSourceHelper() {
                    private int numCalls;

                    @Override
                    protected void makeRequest(
                            TileResponseReceiver tileRespRecv,
                            WMSLayer layer,
                            Map<String, String> wmsParams,
                            MimeType expectedMimeType,
                            Resource target)
                            throws GeoWebCacheException {
                        numCalls++;
                        switch (numCalls) {
                            case 1:
                                throw new GeoWebCacheException("test exception");
                            case 2:
                                throw new RuntimeException("test unexpected exception");
                            case 3:
                                throw new GeoWebCacheException("second test exception");
                            case 4:
                                throw new RuntimeException("second test unexpected exception");
                            default:
                                try {
                                    target.transferFrom(
                                            Channels.newChannel(
                                                    new ByteArrayInputStream(fakeWMSResponse)));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                        }
                    }
                };

        tl.setSourceHelper(mockSourceHelper);

        final int zoomLevel = 4;
        SeedRequest req = createRequest(tl, TYPE.SEED, zoomLevel, zoomLevel);

        TileRange tr = TileBreeder.createTileRange(req, tl);
        TileRangeIterator trIter = new TileRangeIterator(tr, tl.getMetaTilingFactors());

        /*
         * Create a mock storage broker that does nothing
         */
        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        expect(mockStorageBroker.put(anyObject())).andReturn(true).anyTimes();
        expect(mockStorageBroker.get(anyObject())).andReturn(false).anyTimes();
        replay(mockStorageBroker);

        long tileFailureRetryWaitTime = 10;
        int tileFailureRetryCount = 1;
        long totalFailuresBeforeAborting = 4;

        boolean reseed = false;
        SeedTask seedTask = new SeedTask(mockStorageBroker, trIter, tl, reseed, false);
        seedTask.setTaskId(1L);
        seedTask.setThreadInfo(new AtomicInteger(), 0);
        Sleeper sleeper = createMock(Sleeper.class);
        // It's only sleeping on checked exceptions, not sure if this is right or wrong.
        // I added this to test a fix for the duration being incorrect.
        sleeper.sleep(tileFailureRetryWaitTime);
        expectLastCall().times(2);
        replay(sleeper);
        seedTask.sleeper = sleeper;

        AtomicLong sharedFailureCounter = new AtomicLong();
        seedTask.setFailurePolicy(
                tileFailureRetryCount,
                tileFailureRetryWaitTime,
                totalFailuresBeforeAborting,
                sharedFailureCounter);
        /*
         * HACK: avoid SeedTask.getCurrentThreadArrayIndex failure.
         */
        Thread.currentThread().setName("pool-fake-thread-1");

        /*
         * Call the seed process
         */
        seedTask.doAction();
        Assert.assertEquals(totalFailuresBeforeAborting, sharedFailureCounter.get());
        verify(sleeper);
    }

    /**
     * Make sure when seeding a given zoom level, the correct tiles are sent to the {@link
     * StorageBroker}
     */
    @Test
    @SuppressWarnings("serial")
    public void testSeedStoredTiles() throws Exception {

        WMSLayer tl = createWMSLayer("image/png");

        // create an image to be returned by the mock WMSSourceHelper
        // / final byte[] fakeWMSResponse = createFakeSourceImage(tl);
        // WMSSourceHelper that on makeRequest() returns always the saqme fake image
        WMSSourceHelper mockSourceHelper = new MockWMSSourceHelper();
        tl.setSourceHelper(mockSourceHelper);

        final String gridSetId = tl.getGridSubsets().iterator().next();
        final int zoomLevel = 2;
        SeedRequest req = createRequest(tl, TYPE.SEED, zoomLevel, zoomLevel);

        /*
         * Create a mock storage broker that has never an image in its blob store and that captures
         * the TileObject the seeder requests it to store for further test validation
         */
        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        Capture<TileObject> storedObjects = EasyMock.newCapture(CaptureType.ALL);
        expect(mockStorageBroker.put(capture(storedObjects))).andReturn(true).anyTimes();

        expect(mockStorageBroker.get(anyObject())).andReturn(false).anyTimes();
        replay(mockStorageBroker);

        TileRange tr = TileBreeder.createTileRange(req, tl);
        TileRangeIterator trIter = new TileRangeIterator(tr, tl.getMetaTilingFactors());

        boolean reseed = false;
        SeedTask task = new SeedTask(mockStorageBroker, trIter, tl, reseed, false);
        task.setTaskId(1L);
        task.setThreadInfo(new AtomicInteger(), 0);
        Sleeper sleeper = createMock(Sleeper.class);
        // Should not be called
        replay(sleeper);
        task.sleeper = sleeper;
        /*
         * HACK: avoid SeedTask.getCurrentThreadArrayIndex failure.
         */
        Thread.currentThread().setName("pool-fake-thread-1");

        /*
         * Call the seed process
         */
        task.doAction();

        final GridSubset gridSubset = tl.getGridSubset(gridSetId);

        /*
         * Make sure the seed process asked for the expected tiles to be stored
         */

        final long[] coveredGridLevels = gridSubset.getCoverage(zoomLevel);

        // seeding should not include edge tiles produced by the meta tiling that don't fall into
        // the gridsubset's coverage
        long starty = coveredGridLevels[1];
        long startx = coveredGridLevels[0];

        final long expectedSavedTileCount =
                (coveredGridLevels[2] - startx + 1) * (coveredGridLevels[3] - starty + 1);

        List<TileObject> storedTiles = storedObjects.getValues();
        final int seededTileCount = storedTiles.size();

        Assert.assertEquals(expectedSavedTileCount, seededTileCount);

        Set<Tuple<Long>> tileKeys = new TreeSet<>();
        Set<Tuple<Long>> expectedTiles = new TreeSet<>();
        for (long x = startx; x <= coveredGridLevels[2]; x++) {
            for (long y = starty; y <= coveredGridLevels[3]; y++) {
                expectedTiles.add(new Tuple<>(x, y, (long) zoomLevel));
            }
        }
        for (TileObject obj : storedTiles) {
            tileKeys.add(new Tuple<>(obj.getXYZ()[0], obj.getXYZ()[1], obj.getXYZ()[2]));
        }

        Assert.assertEquals(expectedTiles, tileKeys);
        verify(sleeper);
    }

    private static class Tuple<T extends Comparable<T>> implements Comparable<Tuple<T>> {

        private T[] members;

        @SafeVarargs
        public Tuple(T... members) {
            this.members = members;
        }

        @Override
        public int compareTo(Tuple<T> o) {
            if (members == null) {
                if (o.members == null) {
                    return 0;
                } else {
                    return -1;
                }
            }
            if (o.members == null) {
                return 1;
            }
            if (members.length == 0 && o.members.length == 0) {
                return 0;
            }
            if (members.length != o.members.length) {
                throw new IllegalArgumentException("Tuples shall be of the same dimension");
            }
            int comparedVal;
            for (int i = 0; i < members.length; i++) {
                comparedVal = members[i].compareTo(o.members[i]);
                if (comparedVal != 0) {
                    break;
                }
            }
            return 0;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Tuple)) {
                return false;
            }
            return 0 == compareTo((Tuple<T>) o);
        }

        @Override
        public int hashCode() {
            return 17 * Arrays.hashCode(members);
        }
    }
}
