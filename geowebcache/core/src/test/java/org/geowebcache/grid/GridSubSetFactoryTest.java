package org.geowebcache.grid;

import static org.junit.Assert.assertArrayEquals;

import java.util.Collections;
import org.geowebcache.config.DefaultGridsets;
import org.junit.Assert;
import org.junit.Test;

public class GridSubSetFactoryTest {

    GridSetBroker gridSetBroker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));

    @Test
    public void testCoverageBounds() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0, 0);

        long[] ret = grid.getCoverage(0);
        long[] correct = {1, 0, 1, 0, 0};

        assertArrayEquals(correct, ret);
    }

    @Test
    public void testCoverageBounds2() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0, 1);

        long[] ret = grid.getCoverage(1);
        long[] correct = {2, 1, 3, 1, 1};

        assertArrayEquals(correct, ret);
    }

    @Test
    public void testGridNames() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);

        int zoomStart = 3;
        int zoomStop = 9;
        GridSubset grid =
                GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, zoomStart, zoomStop);

        String[] gridNames = grid.getGridNames();
        final int nlevels = 1 + (grid.getZoomStop() - grid.getZoomStart());
        Assert.assertEquals(nlevels, gridNames.length);
        for (String name : gridNames) {
            Assert.assertNotNull(name);
        }
    }

    @Test
    public void testWMTSCoverage() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 1, 3);

        long[][] coverages = grid.getWMTSCoverages();
        Assert.assertEquals(3, coverages.length);
        long[] correct = {2, 0, 3, 0};

        assertArrayEquals(correct, coverages[0]);
    }

    @Test
    public void testGridIndex() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);

        int zoomStart = 3;
        int zoomStop = 9;
        GridSubset grid =
                GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, zoomStart, zoomStop);

        String[] gridNames = grid.getGridNames();
        for (int i = 0, z = zoomStart; i < gridNames.length; i++, z++) {
            Assert.assertEquals(z, grid.getGridIndex(gridNames[i]));
        }
    }
}
