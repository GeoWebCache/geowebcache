package org.geowebcache.storage;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.ServletUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.StopWatch;

public class TileRangeIteratorTest {

    static final Logger LOG = Logging.getLogger(TileRangeIteratorTest.class.getName());

    private MimeType mimeType;

    private String parameters;

    private GridSet gridSet;

    private GridSubset gridSubSet;

    private long[][] gridCoverages;

    /**
     * If non null, {@link #traverseTileRangeIter(int, long[][], int, int, int[])} will create a
     * {@link DiscontinuousTileRange} instead of a simple {@link TileRange}
     */
    private RasterMask rasterMask;

    @Before
    public void setUp() throws Exception {
        mimeType = MimeType.createFromFormat("image/png");
        parameters = null;
        gridSet =
                new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, false)))
                        .getWorldEpsg3857();
        BoundingBox extent = new BoundingBox(0, 0, 100, 100);
        boolean alignTopLeft = false;
        int levels = 12;
        Double metersPerUnit = Double.valueOf(1);
        double pixelSize = 1;
        int tileWidth = 100;
        int tileHeight = 100;
        boolean yCoordinateFirst = false;

        gridSet =
                GridSetFactory.createGridSet(
                        "TestGridSet",
                        SRS.getSRS(100000),
                        extent,
                        alignTopLeft,
                        levels,
                        metersPerUnit,
                        pixelSize,
                        tileWidth,
                        tileHeight,
                        yCoordinateFirst);
        gridSubSet = GridSubsetFactory.createGridSubSet(gridSet);
        gridCoverages = gridSubSet.getCoverages();
    }

    /** */
    @Test
    public void testTraverseIndividualZoomLevelsNoMetaTiling() throws Exception {
        int zoomStart = gridSubSet.getZoomStart();
        int zoomStop = gridSubSet.getZoomStop();
        int[] metaTilingFactors = {1, 1};

        for (int zLevel = zoomStart; zLevel <= zoomStop; zLevel++) {
            long tilesProcessed =
                    traverseTileRangeIter(1, gridCoverages, zLevel, zLevel, metaTilingFactors);
            long expected = countMetaTiles(gridCoverages, zLevel, zLevel, metaTilingFactors);
            Assert.assertEquals(
                    "Expected tile count mismatch at zoom level " + zLevel,
                    expected,
                    tilesProcessed);
        }
    }

    /** */
    @Test
    public void testTraverseIndividualZoomLevelsNoMetaTilingMultiThreading() throws Exception {
        int zoomStart = gridSubSet.getZoomStart();
        int zoomStop = gridSubSet.getZoomStop();
        int[] metaTilingFactors = {1, 1};

        final int nThreads = 64;
        StopWatch sw = new StopWatch();
        sw.start();
        // for (int zLevel = zoomStart; zLevel <= zoomStop; zLevel++) {
        long tilesProcessed =
                traverseTileRangeIter(
                        nThreads, gridCoverages, zoomStart, zoomStop, metaTilingFactors);
        long expected = countMetaTiles(gridCoverages, zoomStart, zoomStop, metaTilingFactors);
        Assert.assertEquals(
                "Expected tile count mismatch at zoom level " + zoomStart,
                expected,
                tilesProcessed);
        // }
        sw.stop();
        LOG.info(
                nThreads
                        + " threads finished in "
                        + sw.getTotalTimeMillis()
                        + " to count "
                        + expected);
    }

    /** */
    @Test
    public void testTraverseIndividualZoomLevelsMetaTiling() throws Exception {
        int zoomStart = gridSubSet.getZoomStart();
        int zoomStop = gridSubSet.getZoomStop();
        int[] metaTilingFactors = {3, 3};

        for (int zLevel = zoomStart; zLevel <= zoomStop; zLevel++) {
            long tilesProcessed =
                    traverseTileRangeIter(1, gridCoverages, zLevel, zLevel, metaTilingFactors);
            long expected = countMetaTiles(gridCoverages, zLevel, zLevel, metaTilingFactors);
            Assert.assertEquals(
                    "Expected tile count mismatch at zoom level " + zLevel,
                    expected,
                    tilesProcessed);
        }
    }

    /** */
    @Test
    public void testWholeRangeMultiThreaded() throws Exception {
        int zoomStart = gridSubSet.getZoomStart();
        int zoomStop = gridSubSet.getZoomStop();
        int[] metaTilingFactors = {1, 1};

        int nThreads = 32;
        long tilesProcessed =
                traverseTileRangeIter(
                        nThreads, gridCoverages, zoomStart, zoomStop, metaTilingFactors);
        long expected = countMetaTiles(gridCoverages, zoomStart, zoomStop, metaTilingFactors);
        Assert.assertEquals(expected, tilesProcessed);
    }

    /** */
    @Test
    public void testWholeRangeMultiThreadedMetaTiling() throws Exception {
        int zoomStart = gridSubSet.getZoomStart();
        int zoomStop = gridSubSet.getZoomStop();
        int[] metaTilingFactors = {3, 3};

        int nThreads = 32;
        long tilesProcessed =
                traverseTileRangeIter(
                        nThreads, gridCoverages, zoomStart, zoomStop, metaTilingFactors);
        long expected = countMetaTiles(gridCoverages, zoomStart, zoomStop, metaTilingFactors);
        Assert.assertEquals(expected, tilesProcessed);
    }

    /** */
    @Test
    public void testDiscontinuousTileRange() throws Exception {
        rasterMask = createMock(RasterMask.class);
        expect(rasterMask.getGridCoverages()).andReturn(gridCoverages);
        // mock up RasterMask to return TRUE only for tiles 0,0,0 and 1,1,1
        expect(rasterMask.lookup(eq(0L), eq(0L), eq(0))).andReturn(Boolean.TRUE);
        expect(rasterMask.lookup(eq(0L), eq(0L), eq(1))).andReturn(Boolean.FALSE);
        expect(rasterMask.lookup(eq(1L), eq(0L), eq(1))).andReturn(Boolean.FALSE);
        expect(rasterMask.lookup(eq(0L), eq(1L), eq(1))).andReturn(Boolean.FALSE);
        expect(rasterMask.lookup(eq(1L), eq(1L), eq(1))).andReturn(Boolean.TRUE);
        replay(rasterMask);

        final int zoomStart = 0;
        final int zoomStop = 1;
        final int[] metaTilingFactors = {1, 1};

        final int nThreads = 1;
        long tilesProcessed =
                traverseTileRangeIter(
                        nThreads, gridCoverages, zoomStart, zoomStop, metaTilingFactors);
        final long expected = 2;
        Assert.assertEquals(expected, tilesProcessed);
        verify(rasterMask);
    }

    /** @return */
    private long traverseTileRangeIter(
            final int nThreads,
            final long[][] coveredGridLevels,
            final int zoomStart,
            final int zoomStop,
            final int[] metaTilingFactors)
            throws Exception {

        @SuppressWarnings("PMD.CloseResource") // implements AutoCloseable in Java 21
        final ExecutorService executorService = Executors.newFixedThreadPool(nThreads);

        final TileRange tileRange;
        if (rasterMask == null) {
            tileRange =
                    new TileRange(
                            "layer",
                            "gridset",
                            zoomStart,
                            zoomStop,
                            coveredGridLevels,
                            mimeType,
                            ServletUtils.queryStringToMap(parameters));
        } else {
            tileRange =
                    new DiscontinuousTileRange(
                            "layer",
                            "gridset",
                            zoomStart,
                            zoomStop,
                            rasterMask,
                            mimeType,
                            ServletUtils.queryStringToMap(parameters));
        }

        final TileRangeIterator tri = new TileRangeIterator(tileRange, metaTilingFactors);

        Collection<Callable<Long>> tasks = new ArrayList<>(nThreads);
        for (int taskN = 0; taskN < nThreads; taskN++) {
            tasks.add(new TileRangeIteratorConsumer(tri));
        }
        List<Future<Long>> values = executorService.invokeAll(tasks);
        executorService.shutdown();
        try {
            executorService.awaitTermination(120, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Assert.fail("Executor service timeout: " + ie.getMessage());
        }

        long totalProcessed = sumValues(values);
        return totalProcessed;
    }

    private long sumValues(List<Future<Long>> values)
            throws InterruptedException, ExecutionException {
        long total = 0;
        for (Future<Long> value : values) {
            long countedByTask = value.get().longValue();
            total += countedByTask;
        }
        return total;
    }

    /**
     * Simple Callable that traverses a {@link TileRangeIterator} and returns the number of grid
     * locations processed
     */
    private static final class TileRangeIteratorConsumer implements Callable<Long> {
        private final TileRangeIterator tri;

        private TileRangeIteratorConsumer(TileRangeIterator tri) {
            this.tri = tri;
        }

        @Override
        public Long call() throws Exception {
            long nprocessed = 0;
            long[] gridLoc = new long[3];
            while (null != (gridLoc = tri.nextMetaGridLocation(gridLoc))) {
                ++nprocessed;
            }
            return Long.valueOf(nprocessed);
        }
    }

    private long countMetaTiles(
            long[][] coveredGridLevels, int startZoom, int stopZoom, int[] metaTilingFactors) {
        long count = 0;

        final int metaX = metaTilingFactors[0];
        final int metaY = metaTilingFactors[1];

        for (int i = startZoom; i <= stopZoom; i++) {
            long[] gridBounds = coveredGridLevels[i];
            long boundsMinX = gridBounds[0];
            long boundsMaxX = gridBounds[2];
            long boundsMinY = gridBounds[1];
            long boundsMaxY = gridBounds[3];

            long tilesX = 1 + boundsMaxX - boundsMinX;
            long tilesY = 1 + boundsMaxY - boundsMinY;

            long metaTilesX = (long) Math.ceil(tilesX / (double) metaX);
            long metaTilesY = (long) Math.ceil(tilesY / (double) metaY);

            long thisLevelMetaTiles = metaTilesX * metaTilesY;

            count += thisLevelMetaTiles;
        }

        return count;
    }
}
