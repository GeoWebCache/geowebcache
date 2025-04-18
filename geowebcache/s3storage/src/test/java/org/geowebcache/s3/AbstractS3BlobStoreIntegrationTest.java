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
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */
package org.geowebcache.s3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.apache.commons.lang3.SystemUtils;
import org.awaitility.Awaitility;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.FileResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Integration tests for {@link S3BlobStore}.
 *
 * <p>This is an abstract class for both online and offline integration tests.
 */
public abstract class AbstractS3BlobStoreIntegrationTest {

    private static Logger log = Logging.getLogger(PropertiesLoader.class.getName());

    private static final String DEFAULT_FORMAT = "png";

    private static final String DEFAULT_GRIDSET = "EPSG:4326";

    private static final String DEFAULT_LAYER = "topp:world";

    public PropertiesLoader testConfigLoader = new PropertiesLoader();

    private S3BlobStore blobStore;

    protected abstract S3BlobStoreInfo getConfiguration();

    @Before
    public void before() throws Exception {
        Assume.assumeFalse("Test skipped on Windows", SystemUtils.IS_OS_WINDOWS);
        Assume.assumeFalse("Test skipped on Mac osx", SystemUtils.IS_OS_MAC_OSX);

        Awaitility.setDefaultPollInterval(10, TimeUnit.MILLISECONDS);
        Awaitility.setDefaultPollDelay(Duration.ZERO);
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30L));

        S3BlobStoreInfo config = getConfiguration();

        TileLayerDispatcher layers = mock(TileLayerDispatcher.class);
        LockProvider lockProvider = new NoOpLockProvider();
        TileLayer layer = mock(TileLayer.class);
        when(layers.getTileLayer(eq(DEFAULT_LAYER))).thenReturn(layer);
        when(layers.getLayerList()).thenReturn(List.of(layer));
        when(layer.getName()).thenReturn(DEFAULT_LAYER);
        when(layer.getId()).thenReturn(DEFAULT_LAYER);
        blobStore = new S3BlobStore(config, layers, lockProvider);
    }

    @After
    public void after() {
        if (blobStore != null) {
            blobStore.destroy();
        }
    }

    @Test
    public void testPutGet() throws MimeException, StorageException {
        byte[] bytes = new byte[1024];
        Arrays.fill(bytes, (byte) 0xaf);
        TileObject tile = queryTile(20, 30, 12);
        tile.setBlob(new ByteArrayResource(bytes));

        blobStore.put(tile);

        TileObject queryTile = queryTile(20, 30, 12);
        boolean found = blobStore.get(queryTile);
        assertTrue(found);
        Resource resource = queryTile.getBlob();
        assertNotNull(resource);
        assertEquals(bytes.length, resource.getSize());
    }

    @Test
    public void testPutGetBlobIsNotByteArrayResource() throws MimeException, IOException {
        File tileFile = File.createTempFile("tile", ".png");
        Files.write(new byte[1024], tileFile);
        Resource blob = new FileResource(tileFile);
        TileObject tile = queryTile(20, 30, 12);
        tile.setBlob(blob);

        blobStore.put(tile);

        TileObject queryTile = queryTile(20, 30, 12);
        boolean found = blobStore.get(queryTile);
        assertTrue(found);
        Resource resource = queryTile.getBlob();
        assertNotNull(resource);
        assertEquals(1024, resource.getSize());
    }

    @Test
    public void testPutWithListener() throws MimeException, StorageException {
        byte[] bytes = new byte[1024];
        Arrays.fill(bytes, (byte) 0xaf);
        TileObject tile = queryTile(20, 30, 12);
        tile.setBlob(new ByteArrayResource(bytes));

        BlobStoreListener listener = mock(BlobStoreListener.class);
        blobStore.addListener(listener);
        blobStore.put(tile);

        verify(listener)
                .tileStored(
                        eq(tile.getLayerName()),
                        eq(tile.getGridSetId()),
                        eq(tile.getBlobFormat()),
                        isNull(),
                        eq(20L),
                        eq(30L),
                        eq(12),
                        eq((long) bytes.length));

        // update tile
        tile = queryTile(20, 30, 12);
        tile.setBlob(new ByteArrayResource(new byte[512]));

        blobStore.put(tile);

        verify(listener)
                .tileUpdated(
                        eq(tile.getLayerName()),
                        eq(tile.getGridSetId()),
                        eq(tile.getBlobFormat()),
                        isNull(),
                        eq(20L),
                        eq(30L),
                        eq(12),
                        eq(512L),
                        eq(1024L));
    }

    @Test
    public void testDelete() throws MimeException, StorageException {
        byte[] bytes = new byte[1024];
        Arrays.fill(bytes, (byte) 0xaf);
        TileObject tile = queryTile(20, 30, 12);
        tile.setBlob(new ByteArrayResource(bytes));

        blobStore.put(tile);

        tile.getXYZ()[0] = 21;
        blobStore.put(tile);

        tile.getXYZ()[0] = 22;
        blobStore.put(tile);

        tile = queryTile(20, 30, 12);

        assertTrue(blobStore.delete(tile));

        tile.getXYZ()[0] = 21;
        assertTrue(blobStore.delete(tile));

        BlobStoreListener listener = mock(BlobStoreListener.class);
        blobStore.addListener(listener);
        tile.getXYZ()[0] = 22;
        assertTrue(blobStore.delete(tile));
        assertFalse(blobStore.delete(tile));

        verify(listener, times(1))
                .tileDeleted(
                        eq(tile.getLayerName()),
                        eq(tile.getGridSetId()),
                        eq(tile.getBlobFormat()),
                        isNull(),
                        eq(22L),
                        eq(30L),
                        eq(12),
                        eq(1024L));
    }

    @Test
    public void testDeleteLayer() throws Exception {
        byte[] bytes = new byte[1024];
        Arrays.fill(bytes, (byte) 0xaf);
        TileObject tile = queryTile(20, 30, 12);
        tile.setBlob(new ByteArrayResource(bytes));

        blobStore.put(tile);

        tile.getXYZ()[0] = 21;
        blobStore.put(tile);

        tile.getXYZ()[0] = 22;
        blobStore.put(tile);

        FakeListener fakeListener = new FakeListener();
        blobStore.addListener(fakeListener);

        String layerName = tile.getLayerName();
        assertTrue(blobStore.delete(layerName));

        Awaitility.await().until(() -> fakeListener.layerDeleted == 1);
        assertEquals(0, fakeListener.tileDeleted);
        assertEquals(1, fakeListener.total());
    }

    @Test
    public void testDeleteGridSubset() throws Exception {
        seed(0, 1, "EPSG:4326", "png", null);
        seed(0, 1, "EPSG:4326", "jpeg", ImmutableMap.of("param", "value"));
        seed(0, 1, "EPSG:3857", "png", null);
        seed(0, 1, "EPSG:3857", "jpeg", ImmutableMap.of("param", "value"));

        assertFalse(blobStore.deleteByGridsetId(DEFAULT_LAYER, "EPSG:26986"));
        assertTrue(blobStore.deleteByGridsetId(DEFAULT_LAYER, "EPSG:4326"));

        assertFalse(blobStore.get(queryTile(DEFAULT_LAYER, "EPSG:4326", "png", 0, 0, 0)));
        assertFalse(blobStore.get(queryTile(DEFAULT_LAYER, "EPSG:4326", "jpeg", 0, 0, 0, "param", "value")));

        assertTrue(blobStore.get(queryTile(DEFAULT_LAYER, "EPSG:3857", "png", 0, 0, 0)));
        assertTrue(blobStore.get(queryTile(DEFAULT_LAYER, "EPSG:3857", "jpeg", 0, 0, 0, "param", "value")));
    }

    // This test is non-deterministic
    @Ignore
    @Test
    public void testLayerMetadata() {
        blobStore.putLayerMetadata(DEFAULT_LAYER, "prop1", "value1");
        blobStore.putLayerMetadata(DEFAULT_LAYER, "prop2", "value2");

        Awaitility.await().untilAsserted(() -> blobStore.getLayerMetadata(DEFAULT_LAYER, "nonExistingKey"));
        Awaitility.await()
                .untilAsserted(() -> assertEquals("value1", blobStore.getLayerMetadata(DEFAULT_LAYER, "prop1")));
        Awaitility.await()
                .untilAsserted(() -> assertEquals("value2", blobStore.getLayerMetadata(DEFAULT_LAYER, "prop2")));
    }

    @Test
    public void testTruncateShortCutsIfNoTilesInParametersPrefix() throws StorageException, MimeException {
        final int zoomStart = 0;
        final int zoomStop = 1;
        seed(zoomStart, zoomStop);
        BlobStoreListener listener = mock(BlobStoreListener.class);
        blobStore.addListener(listener);

        GridSet gridset =
                new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false))).getWorldEpsg4326();
        GridSubset gridSubSet = GridSubsetFactory.createGridSubSet(gridset);

        long[][] rangeBounds = { //
            gridSubSet.getCoverage(0), //
            gridSubSet.getCoverage(1) //
        };

        MimeType mimeType = MimeType.createFromExtension(DEFAULT_FORMAT);
        // use a parameters map for which there're no tiles
        Map<String, String> parameters = ImmutableMap.of("someparam", "somevalue");
        TileRange tileRange =
                tileRange(DEFAULT_LAYER, DEFAULT_GRIDSET, zoomStart, zoomStop, rangeBounds, mimeType, parameters);

        assertFalse(blobStore.delete(tileRange));
        Awaitility.await().untilAsserted(() -> verify(listener, times(0))
                .tileDeleted(
                        anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyInt(), anyLong()));
    }

    @Test
    public void testTruncateShortCutsIfNoTilesInGridsetPrefix() throws StorageException, MimeException {

        final int zoomStart = 0;
        final int zoomStop = 1;
        seed(zoomStart, zoomStop);
        BlobStoreListener listener = mock(BlobStoreListener.class);
        blobStore.addListener(listener);

        // use a gridset for which there're no tiles
        GridSet gridset =
                new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, true))).getWorldEpsg3857();
        GridSubset gridSubSet = GridSubsetFactory.createGridSubSet(gridset);

        long[][] rangeBounds = { //
            gridSubSet.getCoverage(0), //
            gridSubSet.getCoverage(1) //
        };

        MimeType mimeType = MimeType.createFromExtension(DEFAULT_FORMAT);

        Map<String, String> parameters = null;
        TileRange tileRange =
                tileRange(DEFAULT_LAYER, gridset.getName(), zoomStart, zoomStop, rangeBounds, mimeType, parameters);

        assertFalse(blobStore.delete(tileRange));
        Awaitility.await().untilAsserted(() -> verify(listener, times(0))
                .tileDeleted(
                        anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyInt(), anyLong()));
    }

    /** Seed levels 0 to 2, truncate levels 0 and 1, check level 2 didn't get deleted */
    @Test
    public void testTruncateRespectsLevels() throws StorageException, MimeException {
        Assume.assumeFalse("Test skipped on Windows", SystemUtils.IS_OS_WINDOWS);
        final int zoomStart = 0;
        final int zoomStop = 2;

        // use a gridset for which there're no tiles
        GridSet gridset =
                new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, true))).getWorldEpsg3857();
        GridSubset gridSubSet = GridSubsetFactory.createGridSubSet(gridset);

        long[][] rangeBounds = gridSubSet.getCoverages();

        seed(zoomStart, zoomStop, gridset.getName(), DEFAULT_FORMAT, null);

        FakeListener listener = new FakeListener();
        blobStore.addListener(listener);

        MimeType mimeType = MimeType.createFromExtension(DEFAULT_FORMAT);

        Map<String, String> parameters = null;

        final int truncateStart = 0, truncateStop = 1;

        TileRange tileRange = tileRange(
                DEFAULT_LAYER, gridset.getName(), truncateStart, truncateStop, rangeBounds, mimeType, parameters);

        assertTrue(blobStore.delete(tileRange));

        int expectedCount = 5; // 1 for level 0, 4 for level 1, as per seed()
        Awaitility.await().untilAsserted(() -> {
            assertEquals(expectedCount, listener.tileDeleted);
            assertEquals(expectedCount, listener.total());
        });
    }

    @Test
    public void testBoundedLayerDeletion() throws StorageException, MimeException {

        int level = 3;
        seed(level, level);
        FakeListener fakeListener = new FakeListener();
        blobStore.addListener(fakeListener);

        long[][] rangeBounds = {{2, 2, 3, 3, level}};

        MimeType mimeType = MimeType.createFromExtension(DEFAULT_FORMAT);

        Map<String, String> parameters = null;
        TileRange tileRange =
                tileRange(DEFAULT_LAYER, DEFAULT_GRIDSET, level, level, rangeBounds, mimeType, parameters);

        assertTrue(blobStore.delete(tileRange));
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int wantedNumberOfInvocations =
                (int) ((rangeBounds[0][2] - rangeBounds[0][0] + 1) * (rangeBounds[0][3] - rangeBounds[0][1] + 1));
        Awaitility.await().untilAsserted(() -> assertEquals(wantedNumberOfInvocations, fakeListener.tileDeleted));
        assertEquals(wantedNumberOfInvocations, fakeListener.total());
    }

    private TileRange tileRange(
            String layerName,
            String gridSetId,
            int zoomStart,
            int zoomStop,
            long[][] rangeBounds,
            MimeType mimeType,
            Map<String, String> parameters) {

        TileRange tileRange =
                new TileRange(layerName, gridSetId, zoomStart, zoomStop, rangeBounds, mimeType, parameters);
        return tileRange;
    }

    private void seed(int zoomStart, int zoomStop) throws StorageException {
        seed(zoomStart, zoomStop, DEFAULT_GRIDSET, DEFAULT_FORMAT, null);
    }

    private void seed(
            int zoomStart, int zoomStop, String gridset, String formatExtension, Map<String, String> parameters)
            throws StorageException {

        Preconditions.checkArgument(zoomStop < 5, "don't use high zoom levels for integration testing");
        for (int z = zoomStart; z <= zoomStop; z++) {
            int max = (int) Math.pow(2, z);
            for (int x = 0; x < max; x++) {
                for (int y = 0; y < max; y++) {
                    log.fine(String.format("seeding %d,%d,%d", x, y, z));
                    put(x, y, z, gridset, formatExtension, parameters);
                }
            }
        }
    }

    private TileObject put(
            long x, long y, int z, String gridset, String formatExtension, Map<String, String> parameters)
            throws StorageException {
        return put(x, y, z, DEFAULT_LAYER, gridset, formatExtension, parameters);
    }

    private TileObject put(
            long x,
            long y,
            int z,
            String layerName,
            String gridset,
            String formatExtension,
            Map<String, String> parameters)
            throws StorageException {
        byte[] bytes = new byte[256];
        Arrays.fill(bytes, (byte) 0xaf);
        TileObject tile = queryTile(layerName, gridset, formatExtension, x, y, z, parameters);
        tile.setBlob(new ByteArrayResource(bytes));
        blobStore.put(tile);
        return tile;
    }

    private TileObject queryTile(long x, long y, int z) {
        return queryTile(DEFAULT_LAYER, DEFAULT_GRIDSET, DEFAULT_FORMAT, x, y, z);
    }

    private TileObject queryTile(String layer, String gridset, String extension, long x, long y, int z) {
        return queryTile(layer, gridset, extension, x, y, z, (Map<String, String>) null);
    }

    private TileObject queryTile(
            String layer, String gridset, String extension, long x, long y, int z, String... parameters) {

        Map<String, String> parametersMap = null;
        if (parameters != null) {
            parametersMap = new HashMap<>();
            for (int i = 0; i < parameters.length; i += 2) {
                parametersMap.put(parameters[i], parameters[i + 1]);
            }
        }
        return queryTile(layer, gridset, extension, x, y, z, parametersMap);
    }

    private TileObject queryTile(
            String layer, String gridset, String extension, long x, long y, int z, Map<String, String> parameters) {

        String format;
        try {
            format = MimeType.createFromExtension(extension).getFormat();
        } catch (MimeException e) {
            throw new RuntimeException(e);
        }

        TileObject tile = TileObject.createQueryTileObject(layer, new long[] {x, y, z}, gridset, format, parameters);
        return tile;
    }

    static class FakeListener implements BlobStoreListener {
        int tileStored = 0;
        int tileDeleted = 0;
        int tileUpdated = 0;
        int layerDeleted = 0;
        int layerRenamed = 0;
        int gridSubsetDeleted = 0;
        int parametersDeleted = 0;

        @Override
        public void tileStored(
                String layerName,
                String gridSetId,
                String blobFormat,
                String parametersId,
                long x,
                long y,
                int z,
                long blobSize) {
            tileStored++;
        }

        @Override
        public void tileDeleted(
                String layerName,
                String gridSetId,
                String blobFormat,
                String parametersId,
                long x,
                long y,
                int z,
                long blobSize) {
            tileDeleted++;
        }

        @Override
        public void tileUpdated(
                String layerName,
                String gridSetId,
                String blobFormat,
                String parametersId,
                long x,
                long y,
                int z,
                long blobSize,
                long oldSize) {
            tileUpdated++;
        }

        @Override
        public void layerDeleted(String layerName) {
            layerDeleted++;
        }

        @Override
        public void layerRenamed(String oldLayerName, String newLayerName) {
            layerRenamed++;
        }

        @Override
        public void gridSubsetDeleted(String layerName, String gridSetId) {
            gridSubsetDeleted++;
        }

        @Override
        public void parametersDeleted(String layerName, String parametersId) {
            parametersDeleted++;
        }

        public int total() {
            return tileDeleted
                    + tileStored
                    + tileUpdated
                    + layerDeleted
                    + layerRenamed
                    + gridSubsetDeleted
                    + parametersDeleted;
        }
    }
}
