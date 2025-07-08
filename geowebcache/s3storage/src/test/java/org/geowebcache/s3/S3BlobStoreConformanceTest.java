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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.s3;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.easymock.EasyMock;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRange;
import org.junit.Assume;
import org.junit.Rule;

public class S3BlobStoreConformanceTest extends AbstractBlobStoreTest<S3BlobStore> {
    public PropertiesLoader testConfigLoader = new PropertiesLoader();

    @Rule
    public TemporaryS3Folder tempFolder = new TemporaryS3Folder(testConfigLoader.getProperties());

    @Override
    @SuppressWarnings("CatchFail")
    public void createTestUnit() throws Exception {
        Assume.assumeTrue(tempFolder.isConfigured());
        S3BlobStoreInfo config = tempFolder.getConfig();

        TileLayerDispatcher layers = createMock(TileLayerDispatcher.class);
        LockProvider lockProvider = new NoOpLockProvider();
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
                        fail();
                    }
                    return mock;
                })
                .forEach(EasyMock::replay);
        replay(layers);
        store = new S3BlobStore(config, layers, lockProvider);
    }

    @Override
    public void assertTileRangeEmpty(String layerName, GridSet gridSet, String format, TileRange range)
            throws StorageException {
        Awaitility.await().atMost(30, SECONDS).untilAsserted(() -> {
            for (int z = range.getZoomStart(); z <= range.getZoomStop(); z++) {
                long[] bounds = range.rangeBounds(z);
                for (long x = bounds[0]; x <= bounds[2]; x++) {
                    for (long y = bounds[1]; y < bounds[2]; y++) {
                        assertNoTile(layerName, x, y, z, gridSet.getName(), format, null);
                    }
                }
            }
        });
    }
}
