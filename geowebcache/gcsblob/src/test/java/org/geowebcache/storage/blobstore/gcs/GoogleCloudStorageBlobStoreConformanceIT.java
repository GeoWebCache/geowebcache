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
 * @author Gabriel Roldan, Camptocamp, Copyright 2025
 */
package org.geowebcache.storage.blobstore.gcs;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import org.easymock.EasyMock;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.grid.GridSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRange;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class GoogleCloudStorageBlobStoreConformanceIT extends AbstractBlobStoreTest<GoogleCloudStorageBlobStore> {

    @ClassRule
    public static GoogleCloudStorageContainerSupport containerSupport = new GoogleCloudStorageContainerSupport();

    /**
     * Used to create a new {@link GoogleCloudStorageBlobStore} with a different prefix for each test at
     * {@link #createTestUnit()} to isolate tests from each other
     */
    @Rule
    public TestName testName = new TestName();

    @Override
    @Before
    public void createTestUnit() throws Exception {
        TileLayerDispatcher layers = createMockLayerDispatcher();

        String prefix = testName.getMethodName();
        super.store = containerSupport.createBlobStore(prefix, layers);
    }

    private TileLayerDispatcher createMockLayerDispatcher() {
        TileLayerDispatcher layers = createMock(TileLayerDispatcher.class);
        Stream.of("testLayer", "testLayer1", "testLayer2")
                .map(name -> {
                    TileLayer mock = createMock(name, TileLayer.class);
                    expect(mock.getName()).andStubReturn(name);
                    expect(mock.getId()).andStubReturn(name);
                    expect(mock.getGridSubsets()).andStubReturn(Collections.singleton("testGridSet"));
                    expect(mock.getMimeTypes()).andStubReturn(Arrays.asList(org.geowebcache.mime.ImageMime.png));
                    try {
                        expect(layers.getTileLayer(eq(name))).andStubReturn(mock);
                    } catch (GeoWebCacheException e) {
                        throw new IllegalStateException(e);
                    }
                    return mock;
                })
                .forEach(EasyMock::replay);
        replay(layers);
        return layers;
    }

    @Test
    public void testDeleteRangeWithListener() throws StorageException {
        TileLayer layer = EasyMock.createNiceMock("layer", TileLayer.class);
        final String layerName = "testLayer";
        EasyMock.expect(layer.getName()).andStubReturn(layerName);
        GridSet gridSet = new DefaultGridsets(true, false).worldEpsg4326();
        final String format = ImageMime.png.getFormat();
        String content = "sample".repeat(1000);
        String gridsetId = gridSet.getName();

        CollectingListener listener = new CollectingListener();
        store.addListener(listener);

        // store full world coverage for zoom levels 0...2
        setupFullCoverage(layerName, gridSet, format, content, gridsetId, 0, 2);

        // delete full range at zoom level 2
        int z = 2;
        long tilesWide = gridSet.getGrid(z).getNumTilesWide();
        long tilesHigh = gridSet.getGrid(z).getNumTilesHigh();

        long[][] rangeBounds = {{0, 0, tilesWide - 1, tilesHigh - 1, 2}};
        TileRange range = new TileRange(layerName, gridsetId, 2, 2, rangeBounds, ImageMime.png, null);

        store.delete(range);

        final int expectedDeletes = tileCount(range);
        await().atMost(10, SECONDS).untilAsserted(() -> assertEquals(expectedDeletes, listener.tilesDeleted.size()));

        // check tiles in range have have been deleted, but others are there
        assertTileRangeEmpty(layerName, gridSet, format, range);
        assertTile(layerName, 0, 0, 0, gridsetId, format, null, content);
        assertTile(layerName, 1, 0, 0, gridsetId, format, null, content);
    }

    private int tileCount(TileRange range) {
        return (int) GoogleCloudStorageBlobStore.toTileIndices(range).count();
    }
}
