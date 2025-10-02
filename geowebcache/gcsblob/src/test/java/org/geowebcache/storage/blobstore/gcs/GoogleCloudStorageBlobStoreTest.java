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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Blob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.storage.CompositeBlobStore;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.SuitabilityCheckRule;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.UnsuitableStorageException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GoogleCloudStorageBlobStoreTest {

    private GoogleCloudStorageClient mockClient;
    private TileLayerDispatcher mockTld;
    private TileLayer testLayer;

    private CollectingListener collectingListener = new CollectingListener();

    @Rule
    public SuitabilityCheckRule suitability = SuitabilityCheckRule.system();

    @Before
    public void setUp() throws StorageException, GeoWebCacheException {
        mockClient = mock(GoogleCloudStorageClient.class);
        when(mockClient.getLocation()).thenReturn("gs://test-bucket/test-prefix");
        when(mockClient.getPrefix()).thenReturn("test-prefix");
        when(mockClient.getBucket()).thenReturn("test-bucket");
        mockTld = mock(TileLayerDispatcher.class);
        testLayer = mock(TileLayer.class);
        when(testLayer.getId()).thenReturn("testLayer");
        when(mockTld.getTileLayer("testLayer")).thenReturn(testLayer);
    }

    private GoogleCloudStorageBlobStore store() throws StorageException {
        GoogleCloudStorageBlobStore store = new GoogleCloudStorageBlobStore(mockClient, mockTld);
        store.addListener(collectingListener);
        return store;
    }

    @Test
    public void testSuitability_None_EmptyDirectory() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false); // empty
        when(mockClient.blobExists("test-prefix/metadata.properties")).thenReturn(false);
        // Should succeed
        assertNotNull(store());
    }

    @Test
    public void testSuitability_None_DirectoryWithFiles() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(true); // has files
        when(mockClient.blobExists("test-prefix/metadata.properties")).thenReturn(false); // no metadata
        // Should succeed (NONE mode allows anything)
        assertNotNull(store());
    }

    @Test
    public void testSuitability_None_DirectoryWithMetadata() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(true);
        when(mockClient.blobExists("test-prefix/metadata.properties")).thenReturn(true); // has metadata
        // Should succeed
        assertNotNull(store());
    }

    @Test
    public void testSuitability_Empty_EmptyDirectory() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false); // empty
        when(mockClient.blobExists("test-prefix/metadata.properties")).thenReturn(false);
        // Should succeed
        assertNotNull(store());
    }

    @Test
    public void testSuitability_Empty_DirectoryWithFiles_NoMetadata() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        when(mockClient.directoryExists("test-prefix")).thenReturn(true); // not empty
        when(mockClient.blobExists("test-prefix/metadata.properties")).thenReturn(false);
        // Should fail - directory not empty
        assertThrows(UnsuitableStorageException.class, this::store);
    }

    @Test
    public void testSuitability_Empty_DirectoryWithMetadata() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        when(mockClient.directoryExists("test-prefix")).thenReturn(true); // not empty
        when(mockClient.blobExists("test-prefix/metadata.properties")).thenReturn(true);
        // Should fail - directory not empty
        assertThrows(UnsuitableStorageException.class, this::store);
    }

    @Test
    public void testSuitability_Existing_EmptyDirectory() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false); // empty
        when(mockClient.blobExists("test-prefix/metadata.properties")).thenReturn(false);
        // Should succeed (fallthrough to EMPTY case)
        assertNotNull(store());
    }

    @Test
    public void testSuitability_Existing_DirectoryWithFiles_NoMetadata() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        when(mockClient.directoryExists("test-prefix")).thenReturn(true); // not empty
        when(mockClient.blobExists("test-prefix/metadata.properties")).thenReturn(false); // no metadata
        // Should fail - has files but no metadata
        assertThrows(UnsuitableStorageException.class, this::store);
    }

    @Test
    public void testSuitability_Existing_DirectoryWithMetadata() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        when(mockClient.directoryExists("test-prefix")).thenReturn(true);
        when(mockClient.blobExists("test-prefix/metadata.properties")).thenReturn(true); // has metadata
        // Should succeed - is existing GWC cache
        assertNotNull(store());
    }

    @Test
    public void testLayerExists() throws StorageException, GeoWebCacheException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix/testLayer/")).thenReturn(true);

        GoogleCloudStorageBlobStore store = store();
        assertTrue(store.layerExists("testLayer"));
        verify(mockClient).directoryExists("test-prefix/testLayer/");

        TileLayer testLayer2 = mock(TileLayer.class);
        when(testLayer2.getId()).thenReturn("testLayer2");
        when(mockTld.getTileLayer("testLayer2")).thenReturn(testLayer2);
        assertFalse(store.layerExists("testLayer2"));
    }

    @Test
    public void testLayerExists_NotFound() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false);
        when(mockClient.directoryExists("test-prefix/testLayer")).thenReturn(false);

        GoogleCloudStorageBlobStore store = store();
        assertFalse(store.layerExists("testLayer"));
    }

    @Test
    public void testGet_ExistingTile() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false);

        Blob mockBlob = mock(Blob.class);
        byte[] content = "tile-data".getBytes();
        when(mockBlob.getContent()).thenReturn(content);
        when(mockBlob.getUpdateTimeOffsetDateTime()).thenReturn(OffsetDateTime.now());
        when(mockClient.get(anyString())).thenReturn(Optional.of(mockBlob));

        GoogleCloudStorageBlobStore store = store();
        TileObject tile =
                TileObject.createQueryTileObject("testLayer", new long[] {0, 0, 0}, "EPSG:4326", "image/png", null);

        boolean found = store.get(tile);

        assertTrue(found);
        assertNotNull(tile.getBlob());
        assertEquals(content.length, tile.getBlobSize());
    }

    @Test
    public void testGet_NonExistingTile() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false);
        when(mockClient.get(anyString())).thenReturn(Optional.empty());

        GoogleCloudStorageBlobStore store = store();
        TileObject tile =
                TileObject.createQueryTileObject("testLayer", new long[] {0, 0, 0}, "EPSG:4326", "image/png", null);

        boolean found = store.get(tile);

        assertFalse(found);
        assertNull(tile.getBlob());
        assertEquals(0, tile.getBlobSize());
    }

    @Test
    public void testPut() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false);

        GoogleCloudStorageBlobStore store = store();
        TileObject tile = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0, 0, 0},
                "EPSG:4326",
                "image/png",
                null,
                new ByteArrayResource("data".getBytes()));

        store.put(tile);

        verify(mockClient)
                .put(eq("test-prefix/testLayer/EPSG:4326/png/default/0/0/0.png"), any(Resource.class), eq("image/png"));
        assertEquals(1, collectingListener.tilesStored.size());
    }

    @Test
    public void testPutOverrides() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false);

        GoogleCloudStorageBlobStore store = store();
        TileObject tile = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0, 0, 0},
                "EPSG:4326",
                "image/png",
                null,
                new ByteArrayResource("data".getBytes()));

        store.put(tile);

        String tileKey = "test-prefix/testLayer/EPSG:4326/png/default/0/0/0.png";
        verify(mockClient).put(eq(tileKey), any(Resource.class), eq("image/png"));

        tile.setBlob(new ByteArrayResource("updated".getBytes()));
        when(mockClient.getSize(tileKey)).thenReturn(OptionalLong.of(100));
        store.put(tile);

        assertEquals(1, collectingListener.tilesStored.size());
        assertEquals(1, collectingListener.tilesUpdated.size());
    }

    @Test
    public void testDeleteLayer() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false);
        when(mockClient.deleteDirectory("test-prefix/testLayer")).thenReturn(true);

        GoogleCloudStorageBlobStore store = store();
        when(mockClient.deleteDirectory("test-prefix/testLayer/")).thenReturn(true);

        boolean deleted = store.delete("testLayer");

        assertTrue(deleted);
        verify(mockClient).deleteBlob("test-prefix/testLayer/metadata.properties");
        verify(mockClient).deleteDirectory("test-prefix/testLayer/");
        assertEquals(List.of("testLayer"), collectingListener.layersDeleted);
    }

    @Test
    public void testDeleteTile() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false);
        when(mockClient.deleteBlob(anyString())).thenReturn(true);

        GoogleCloudStorageBlobStore store = store();
        TileObject tile =
                TileObject.createQueryTileObject("testLayer", new long[] {0, 0, 0}, "EPSG:4326", "image/png", null);

        String tileKey = "test-prefix/testLayer/EPSG:4326/png/default/0/0/0.png";
        when(mockClient.getSize(tileKey)).thenReturn(OptionalLong.of(100));

        boolean deleted = store.delete(tile);
        assertTrue(deleted);
        verify(mockClient).deleteBlob(anyString());
        assertEquals(1, collectingListener.tilesDeleted.size());
    }

    @Test
    public void testRename() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false);
        when(mockClient.directoryExists("oldLayer")).thenReturn(true);

        GoogleCloudStorageBlobStore store = store();
        boolean renamed = store.rename("oldLayer", "newLayer");

        assertTrue(renamed);
        // GCS store uses layer ID, so no actual rename needed
        verify(mockClient, never()).deleteDirectory(anyString());
    }

    @Test
    public void testDestroy() throws StorageException {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        when(mockClient.directoryExists("test-prefix")).thenReturn(false);

        GoogleCloudStorageBlobStore store = store();
        store.destroy();

        verify(mockClient).close();
    }

    @Test
    public void testDeleteByParametersId() throws StorageException {
        when(testLayer.getGridSubsets()).thenReturn(Set.of("EPSG:4326", "EPSG:3857"));
        when(testLayer.getMimeTypes()).thenReturn(List.of(ImageMime.png, ImageMime.jpeg));

        GoogleCloudStorageBlobStore store = store();

        when(mockClient.deleteDirectory("test-prefix/testLayer/EPSG:4326/png/test-params-id/"))
                .thenReturn(true);

        assertTrue(store.deleteByParametersId("testLayer", "test-params-id"));

        verify(mockClient).deleteDirectory("test-prefix/testLayer/EPSG:4326/png/test-params-id/");
        verify(mockClient).deleteDirectory("test-prefix/testLayer/EPSG:3857/jpeg/test-params-id/");
        verify(mockClient).deleteDirectory("test-prefix/testLayer/EPSG:3857/png/test-params-id/");
        verify(mockClient).deleteDirectory("test-prefix/testLayer/EPSG:4326/jpeg/test-params-id/");

        Map<String, List<String>> expected = Map.of("testLayer", List.of("test-params-id"));
        assertEquals(expected, collectingListener.parametersDeleted);
    }

    @Test
    public void testDeleteByGridsetId() throws StorageException {
        when(testLayer.getGridSubsets()).thenReturn(Set.of("EPSG:4326", "EPSG:3857"));

        when(mockClient.deleteDirectory("test-prefix/testLayer/EPSG:4326/")).thenReturn(true);
        when(mockClient.deleteDirectory("test-prefix/testLayer/EPSG:3857/")).thenReturn(true);

        GoogleCloudStorageBlobStore store = store();
        assertTrue(store.deleteByGridsetId("testLayer", "EPSG:4326"));
        assertTrue(store.deleteByGridsetId("testLayer", "EPSG:3857"));

        Map<String, List<String>> expected = Map.of("testLayer", List.of("EPSG:4326", "EPSG:3857"));
        assertEquals(expected, collectingListener.gridSubsetsDeleted);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDeleteTileRange() throws StorageException {
        long[][] rangeBounds = {{0, 0, 4 - 1, 4 - 1, 2}};
        TileRange range = new TileRange("testLayer", "EPSG:4326", 2, 2, rangeBounds, ImageMime.png, null);

        when(mockClient.directoryExists("test-prefix/testLayer/EPSG:4326/png/default"))
                .thenReturn(true);

        // Capture the arguments passed to client.delete()
        ArgumentCaptor<Stream<TileLocation>> streamCaptor = ArgumentCaptor.forClass(Stream.class);
        ArgumentCaptor<BiConsumer<TileLocation, Long>> callbackCaptor = ArgumentCaptor.forClass(BiConsumer.class);

        GoogleCloudStorageBlobStore store = store();
        assertTrue(store.delete(range));

        verify(mockClient).directoryExists("test-prefix/testLayer/EPSG:4326/png/default");
        verify(mockClient).delete(streamCaptor.capture(), callbackCaptor.capture());

        // Verify the stream contains the expected tiles
        List<TileLocation> tiles = streamCaptor.getValue().collect(Collectors.toList());
        assertEquals(16, tiles.size()); // 4x4 grid at zoom level 2

        // Verify the callback works - it should notify the listener
        // simulate deleteService.submit(() -> deleteInternal(keys, callback)); at client.delete(Stream, Biconsumer);
        BiConsumer<TileLocation, Long> callback = callbackCaptor.getValue();

        tiles.forEach(tile -> callback.accept(tile, 100L));

        // Verify the listener was notified
        assertEquals(16, collectingListener.tilesDeleted.size());
    }

    @Test
    public void testGetLayerMetadata() throws StorageException {
        GoogleCloudStorageBlobStore store = store();
        when(mockClient.get("test-prefix/testLayer/metadata.properties")).thenReturn(Optional.empty());
        assertNull(store.getLayerMetadata("testLayer", "testKey"));

        Blob mockBlob = mock(Blob.class);
        byte[] value = "testKey=testValue".getBytes(StandardCharsets.UTF_8);
        when(mockBlob.getContent()).thenReturn(value);
        when(mockClient.get("test-prefix/testLayer/metadata.properties")).thenReturn(Optional.of(mockBlob));

        assertEquals("testValue", store.getLayerMetadata("testLayer", "testKey"));
    }

    @Test
    public void testPutLayerMetadata() throws IOException {
        mockBlob("test-prefix/testLayer/metadata.properties", "existingKey=testValue");

        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);

        GoogleCloudStorageBlobStore store = store();
        store.putLayerMetadata("testLayer", "newKey", "value2");

        verify(mockClient)
                .put(eq("test-prefix/testLayer/metadata.properties"), contentCaptor.capture(), eq("text/plain"));

        byte[] content = contentCaptor.getValue();
        Properties props = new Properties();
        props.load(new ByteArrayInputStream(content));
        assertEquals("testValue", props.getProperty("existingKey"));
        assertEquals("value2", props.getProperty("newKey"));
    }

    @Test
    public void testGetParametersMapping() throws StorageException {
        GoogleCloudStorageBlobStore store = store();

        Map<String, Optional<Map<String, String>>> mappings = store.getParametersMapping("testLayer");
        assertNotNull(mappings);
        assertTrue(mappings.isEmpty());

        final String paramsPrefix = "test-prefix/testLayer/parameters-";

        Map<String, String> params1 = Map.of("key1", "value1");
        Map<String, String> params2 = Map.of("key2", "value2");

        String paramsId1 = ParametersUtils.getId(params1);
        String paramsId2 = ParametersUtils.getId(params2);

        Blob paramsBlob1 = mockBlob(paramsPrefix + paramsId1, "key1=value1");
        Blob paramsBlob2 = mockBlob(paramsPrefix + paramsId2, "key2=value2");

        when(mockClient.list(paramsPrefix)).thenReturn(Stream.of(paramsBlob1, paramsBlob2));

        mappings = store.getParametersMapping("testLayer");
        assertNotNull(mappings);
        assertEquals(Optional.of(params1), mappings.get(paramsId1));
        assertEquals(Optional.of(params2), mappings.get(paramsId2));
    }

    @Test
    public void testClearUnsupported() throws StorageException {
        GoogleCloudStorageBlobStore store = store();
        assertThrows(UnsupportedOperationException.class, store::clear);
    }

    private Blob mockBlob(String key, String val) throws StorageException {
        Blob mockBlob = mock(Blob.class);
        byte[] value = val.getBytes(StandardCharsets.UTF_8);
        when(mockBlob.getContent()).thenReturn(value);
        when(mockClient.get(key)).thenReturn(Optional.of(mockBlob));
        when(mockBlob.getSize()).thenReturn((long) value.length);
        when(mockBlob.getName()).thenReturn(key);
        return mockBlob;
    }
}
