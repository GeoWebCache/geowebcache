package org.geowebcache.georss;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import junit.framework.TestCase;

import org.geowebcache.georss.DefaultSeedAreaProvider;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.GridLocObj;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.rest.GWCTask.TYPE;
import org.geowebcache.rest.seed.SeedRequest;
import org.geowebcache.util.TestUtils;

public class DefaultSeedAreaProviderTest extends TestCase {

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testGetTotalTileCount() {
        long totalTileCount;
        int zoomStart;
        int zoomStop;
        DefaultSeedAreaProvider provider;

        zoomStart = 0;
        zoomStop = 0;
        provider = createProvider(zoomStart, zoomStop, 3, 3);
        totalTileCount = provider.getTotalTileCount();
        assertEquals(2, totalTileCount);

        zoomStart = 1;
        zoomStop = 1;
        provider = createProvider(zoomStart, zoomStop, 3, 3);
        totalTileCount = provider.getTotalTileCount();
        assertEquals(6, totalTileCount);

        zoomStart = 2;
        zoomStop = 2;
        provider = createProvider(zoomStart, zoomStop, 3, 3);
        totalTileCount = provider.getTotalTileCount();
        assertEquals(6, totalTileCount);

        zoomStart = 3;
        zoomStop = 3;
        provider = createProvider(zoomStart, zoomStop, 3, 3);
        totalTileCount = provider.getTotalTileCount();
        assertEquals(12, totalTileCount);

        zoomStart = 0;
        zoomStop = 3;
        provider = createProvider(zoomStart, zoomStop, 3, 3);
        totalTileCount = provider.getTotalTileCount();
        assertEquals(26, totalTileCount);
    }

    public void testNextGridLocationMetaTiling() {
        int zoomStart;
        int zoomStop;
        DefaultSeedAreaProvider provider;

        zoomStart = 0;
        zoomStop = 0;
        provider = createProvider(zoomStart, zoomStop, 3, 3);

        long[] loc;
        loc = provider.nextGridLocation();
        assertTrue(Arrays.toString(loc), Arrays.equals(new long[] { 0, 0, 0 }, loc));

        loc = provider.nextGridLocation();
        assertNull(loc);// no more tiles due to metatiling

        zoomStart = 0;
        zoomStop = 3;
        provider = createProvider(zoomStart, zoomStop, 3, 3);

        loc = provider.nextGridLocation();
        assertTrue(Arrays.toString(loc), Arrays.equals(new long[] { 0, 0, 0 }, loc));

        loc = provider.nextGridLocation();
        assertTrue(Arrays.toString(loc), Arrays.equals(new long[] { 0, 0, 1 }, loc));

        loc = provider.nextGridLocation();
        assertTrue(Arrays.toString(loc), Arrays.equals(new long[] { 3, 0, 2 }, loc));

        loc = provider.nextGridLocation();
        assertTrue(Arrays.toString(loc), Arrays.equals(new long[] { 6, 3, 3 }, loc));

        loc = provider.nextGridLocation();
        assertTrue(Arrays.toString(loc), Arrays.equals(new long[] { 9, 3, 3 }, loc));

        loc = provider.nextGridLocation();
        assertNull(loc);// no more tiles due to metatiling
    }

    public void testNextGridLocationNoMetaTiling() {
        int zoomStart;
        int zoomStop;
        DefaultSeedAreaProvider provider;

        zoomStart = 0;
        zoomStop = 3;
        final int metaTileFactorX = 1;
        final int metaTileFactorY = 1;

        provider = createProvider(zoomStart, zoomStop, metaTileFactorX, metaTileFactorY);

        long[][] expected = { { 0, 0, 0 },// 
                { 1, 0, 0 },// 
                { 1, 1, 1 },// 
                { 2, 1, 1 },// 
                { 3, 2, 2 },// 
                { 4, 2, 2 },// 
                { 6, 4, 3 },// 
                { 7, 4, 3 }, // 
                { 8, 4, 3 }, // 
                { 9, 4, 3 }, // 
                { 6, 5, 3 }, // 
                { 7, 5, 3 }, // 
                { 8, 5, 3 }, // 
                { 9, 5, 3 } // 
        };
        final int count = expected.length;
        long[][] actual = new long[count][];
        for (int i = 0; i < count; i++) {
            actual[i] = provider.nextGridLocation();
        }

        assertNull(provider.nextGridLocation());// no more tiles

        TestUtils.assertEquals(expected, actual);

    }

    public void testNextGridLocationNoMetaTilingMultithreading() {
        final int metaTileFactorX = 1;
        final int metaTileFactorY = 1;
        final GridLocObj[] expectedLocations = {//
        glo(0, 0, 0),// 
                glo(1, 0, 0),// 
                glo(1, 1, 1),// 
                glo(2, 1, 1),// 
                glo(3, 2, 2),// 
                glo(4, 2, 2),// 
                glo(6, 4, 3),// 
                glo(7, 4, 3), // 
                glo(8, 4, 3), // 
                glo(9, 4, 3), // 
                glo(6, 5, 3), // 
                glo(7, 5, 3), // 
                glo(8, 5, 3), // 
                glo(9, 5, 3) //
        };

        testNextMultiThreaded(metaTileFactorX, metaTileFactorY, expectedLocations);
    }

    /**
     * helper function to create a {@link GridLocObj} when the constructor's second parameter
     * doesn't matter (since we use them here only to (ab)use the equals method)
     * 
     * @param x
     * @param y
     * @param z
     * @return
     */
    private GridLocObj glo(long x, long y, long z) {
        return new GridLocObj(new long[] { x, y, z }, 7);
    }

    public void testNextGridLocationMetaTilingMultithreading() {
        final int metaTileFactorX = 3;
        final int metaTileFactorY = 3;
        final GridLocObj[] expectedLocations = {//
        glo(0, 0, 0),// 
                glo(0, 0, 1),// 
                glo(3, 0, 2),// 
                glo(6, 3, 3),// 
                glo(9, 3, 3) };
        testNextMultiThreaded(metaTileFactorX, metaTileFactorY, expectedLocations);
    }

    /**
     * A single provider might have more than one consumer (seed tasks)
     * 
     * @param expectedLocations
     */
    private void testNextMultiThreaded(final int metaTileFactorX, final int metaTileFactorY,
            final GridLocObj[] expectedLocations) {
        final int zoomStart = 0;
        final int zoomStop = 3;
        final DefaultSeedAreaProvider provider = createProvider(zoomStart, zoomStop,
                metaTileFactorX, metaTileFactorY);

        final int numThreads = 4;
        ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
        final List<GridLocObj> gatheredLocations = Collections
                .synchronizedList(new ArrayList<GridLocObj>());

        for (int taskN = 0; taskN < numThreads; taskN++) {
            threadPool.submit(new Callable<Object>() {
                /**
                 * @see java.util.concurrent.Callable#call()
                 */
                public Object call() throws Exception {
                    long[] gridLocation;
                    while ((gridLocation = provider.nextGridLocation()) != null) {
                        GridLocObj c = glo(gridLocation[0], gridLocation[1], gridLocation[2]);
                        gatheredLocations.add(c);
                    }
                    // no more tiles
                    return null;
                }
            });
        }

        // call shutdown to block until all tasks finish
        threadPool.shutdown();
        while (!threadPool.isTerminated()) {
            Thread.yield();
        }

        Set<GridLocObj> expected = new HashSet<GridLocObj>(Arrays.asList(expectedLocations));

        assertEquals(gatheredLocations.toString(), expected.size(), gatheredLocations.size());
        assertEquals(expected, new HashSet<GridLocObj>(gatheredLocations));
    }

    private DefaultSeedAreaProvider createProvider(int zoomStart, int zoomStop,
            int metaTileFactorX, int metaTileFactorY) {
        WMSLayer layer = TestUtils.createWMSLayer("image/png", new GridSetBroker(false, false),
                metaTileFactorX, metaTileFactorY, new BoundingBox(-30.0, 15.0, 45.0, 30));
        SeedRequest request = TestUtils.createSeedRequest(layer, TYPE.SEED, zoomStart, zoomStop);

        DefaultSeedAreaProvider provider = new DefaultSeedAreaProvider(request, layer);
        return provider;
    }

}
