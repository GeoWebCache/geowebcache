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
package org.geowebcache.rest.seed;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.replay;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.classextension.EasyMock;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.layer.wms.WMSMetaTile;
import org.geowebcache.layer.wms.WMSSourceHelper;
import org.geowebcache.rest.GWCTask.TYPE;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileObject;

import com.vividsolutions.jts.geom.Coordinate;

/**
 * Unit test suite for {@link SeedTask}
 * 
 * @author Gabriel Roldan (OpenGeo)
 * @version $Id$
 */
public class SeedTaskTest extends TestCase {

    private final GridSetBroker gridSetBroker = new GridSetBroker(false, false);

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * For a metatiled seed request over a given zoom level, make sure the correct wms calls are
     * issued
     * 
     * @throws Exception
     */
    @SuppressWarnings("serial")
    public void testSeedWMSRequests() throws Exception {
        WMSLayer tl = createWMSLayer("image/png");

        // create an image to be returned by the mock WMSSourceHelper
        final byte[] fakeWMSResponse = createFakeSourceImage(tl);

        // WMSSourceHelper that on makeRequest() returns always the saqme fake image
        WMSSourceHelper mockSourceHelper = EasyMock.createMock(WMSSourceHelper.class);

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
        SeedRequest req = createRequest(tl, TYPE.SEED, zoomLevel, zoomLevel);

        /*
         * Create a mock storage broker that does nothing
         */
        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        expect(mockStorageBroker.put((TileObject) anyObject())).andReturn(true).anyTimes();
        expect(mockStorageBroker.get((TileObject) anyObject())).andReturn(false).anyTimes();
        replay(mockStorageBroker);

        boolean reseed = false;
        SeedTask seedTask = new SeedTask(mockStorageBroker, req, tl, reseed);
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

        WMSLayer tl = createWMSLayer("image/png");

        // create an image to be returned by the mock WMSSourceHelper
        final byte[] fakeWMSResponse = createFakeSourceImage(tl);
        // WMSSourceHelper that on makeRequest() returns always the saqme fake image
        WMSSourceHelper mockSourceHelper = EasyMock.createMock(WMSSourceHelper.class);
        expect(mockSourceHelper.makeRequest((WMSMetaTile) anyObject())).andReturn(fakeWMSResponse)
                .anyTimes();
        replay(mockSourceHelper);
        tl.setSourceHelper(mockSourceHelper);

        final String gridSetId = tl.getGridSubsets().keySet().iterator().next();
        final int zoomLevel = 2;
        SeedRequest req = createRequest(tl, TYPE.SEED, zoomLevel, zoomLevel);

        /*
         * Create a mock storage broker that has never an image in its blob store and that captures
         * the TileObject the seeder requests it to store for further test validation
         */
        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
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
        SeedTask task = new SeedTask(mockStorageBroker, req, tl, reseed);
        task.setTaskId(1L);
        task.setThreadInfo(1, 0);
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

    private SeedRequest createRequest(WMSLayer tl, TYPE type, int zoomStart, int zoomStop) {
        String gridSet = tl.getGridSubsets().keySet().iterator().next();
        BoundingBox bounds = null;
        int threadCount = 1;
        String format = tl.getMimeTypes().get(0).getFormat();
        SeedRequest req = new SeedRequest(tl.getName(), bounds, gridSet, threadCount, zoomStart,
                zoomStop, format, type, null);
        return req;

    }

    private WMSLayer createWMSLayer(final String format) {

        String[] urls = { "http://localhost:38080/wms" };
        List<String> formatList = Collections.singletonList(format);

        Hashtable<String, GridSubset> grids = new Hashtable<String, GridSubset>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326,
                new BoundingBox(-30.0, 15.0, 45.0, 30), 0, 10);

        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = { 3, 3 };

        WMSLayer layer = new WMSLayer("test:layer", urls, "aStyle", "test:layer", formatList,
                grids, metaWidthHeight, "vendorparam=true", false);

        layer.initialize(gridSetBroker);

        return layer;
    }

    private byte[] createFakeSourceImage(final WMSLayer layer) throws IOException {

        int tileWidth = layer.getGridSubset(gridSetBroker.WORLD_EPSG4326.getName()).getGridSet()
                .getTileWidth();
        int tileHeight = layer.getGridSubset(gridSetBroker.WORLD_EPSG4326.getName()).getGridSet()
                .getTileHeight();

        int width = tileWidth * layer.getMetaTilingFactors()[0];
        int height = tileHeight * layer.getMetaTilingFactors()[1];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RenderedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        String formatName = layer.getMimeTypes().get(0).getInternalName();
        ImageIO.write(image, formatName, out);
        return out.toByteArray();
    }
}
