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
 * @author Stuart Adam, Ordnance Survey, Copyright 2017
 */
package org.geowebcache.nested;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.google.common.base.Throwables;

public class NestedBlobStoreTest {
    private static final String DEFAULT_FORMAT = "png";

    private static final String DEFAULT_GRIDSET = "EPSG:4326";

    private static final String DEFAULT_LAYER = "topp:world";

    @Mock
    BlobStore frontStore;

    @Mock
    BlobStore backingStore;

    @Mock
    NestedBlobStoreConfig config;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetFromFrontStore() throws StorageException {
        TileObject to = configureQuery();
        NestedBlobStore store = configureStore();

        Mockito.when(frontStore.get(to)).thenReturn(true);

        store.get(to);
        Mockito.verify(frontStore).get(to);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testGetFromBackingStoreAndPopulateFrontStore() throws StorageException {
        TileObject to = configureQuery();
        NestedBlobStore store = configureStore();

        Mockito.when(frontStore.get(to)).thenReturn(false);
        Mockito.when(backingStore.get(to)).thenReturn(true);

        store.get(to);
        Mockito.verify(frontStore).get(to);
        Mockito.verify(backingStore).get(to);
        Mockito.verify(frontStore).put(to);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testFailedGetFromFrontBackingStore() throws StorageException {
        TileObject to = configureQuery();
        NestedBlobStore store = configureStore();

        Mockito.when(frontStore.get(to)).thenReturn(false);
        Mockito.when(backingStore.get(to)).thenReturn(false);

        store.get(to);
        Mockito.verify(frontStore).get(to);
        Mockito.verify(backingStore).get(to);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testPut() throws StorageException {
        TileObject to = configureQuery();
        NestedBlobStore store = configureStore();

        store.put(to);
        Mockito.verify(frontStore).put(to);
        Mockito.verify(backingStore).put(to);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testGetLayerMetaDataFrontStore() throws StorageException {
        TileObject to = configureQuery();
        NestedBlobStore store = configureStore();

        String key = "key";
        String layerName = "layer";
        Mockito.when(frontStore.getLayerMetadata(layerName, key)).thenReturn("value");

        store.getLayerMetadata(layerName, key);
        Mockito.verify(frontStore).getLayerMetadata(layerName, key);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testGetLayerMetaDataBackingStoreIfFrontStoreEmpty() throws StorageException {
        NestedBlobStore store = configureStore();

        String key = "key";
        String layerName = "layer";
        Mockito.when(frontStore.getLayerMetadata(layerName, key)).thenReturn(null);
        Mockito.when(backingStore.getLayerMetadata(layerName, key)).thenReturn("value");

        store.getLayerMetadata(layerName, key);
        Mockito.verify(frontStore).getLayerMetadata(layerName, key);
        Mockito.verify(backingStore).getLayerMetadata(layerName, key);
        Mockito.verify(frontStore).putLayerMetadata(layerName, key, "value");
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testPutLayerMetaData() throws StorageException {
        NestedBlobStore store = configureStore();

        String key = "key";
        String layerName = "layer";
        String value = "value";

        store.putLayerMetadata(layerName, key, value);
        Mockito.verify(frontStore).putLayerMetadata(layerName, key, value);
        Mockito.verify(backingStore).putLayerMetadata(layerName, key, value);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testDeleteTileObject() throws StorageException {
        TileObject to = configureQuery();
        NestedBlobStore store = configureStore();

        store.delete(to);
        Mockito.verify(frontStore).delete(to);
        Mockito.verify(backingStore).delete(to);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testDeleteTileRange() throws StorageException, MimeException {
        long[][] rangeBounds = { //
                { 0, 0, 0, 0, 0 }, //
                { 0, 0, 1, 1, 1 }, //
                { 0, 0, 3, 3, 2 } //
        };

        MimeType mimeType = MimeType.createFromExtension(DEFAULT_FORMAT);

        Map<String, String> parameters = null;

        final int truncateStart = 0, truncateStop = 1;

        TileRange tileRange = tileRange(DEFAULT_LAYER, DEFAULT_GRIDSET, truncateStart, truncateStop,
                rangeBounds, mimeType, parameters);
        NestedBlobStore store = configureStore();

        store.delete(tileRange);
        Mockito.verify(frontStore).delete(tileRange);
        Mockito.verify(backingStore).delete(tileRange);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testDeleteLayerName() throws StorageException {
        String layer = "layer";
        NestedBlobStore store = configureStore();

        store.delete(layer);
        Mockito.verify(frontStore).delete(layer);
        Mockito.verify(backingStore).delete(layer);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testDeleteByGridsetId() throws StorageException {
        String layer = "layer";
        String gridSet = "gridSet";
        NestedBlobStore store = configureStore();

        store.deleteByGridsetId(layer, gridSet);
        Mockito.verify(frontStore).deleteByGridsetId(layer, gridSet);
        Mockito.verify(backingStore).deleteByGridsetId(layer, gridSet);
        Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }

    @Test
    public void testDeleteByParametersId() throws StorageException {
       NestedBlobStore store = configureStore();
       String parametersId="parametersId";
       String layer = "layer";
       store.deleteByParametersId(layer , parametersId);
       Mockito.verify(frontStore).deleteByParametersId(layer, parametersId);
       Mockito.verify(backingStore).deleteByParametersId(layer, parametersId);
       Mockito.verifyNoMoreInteractions(frontStore, backingStore);
    }
    
    @Test
    public void testRename() throws StorageException {
        NestedBlobStore store = configureStore();
        String oldLayerName = "oldLayerName";
        String newLayerName = "newLayerName";
        store.rename(oldLayerName, newLayerName);
        Mockito.verify(frontStore).rename(oldLayerName, newLayerName);
        Mockito.verify(backingStore).rename(oldLayerName, newLayerName);
        Mockito.verifyZeroInteractions(frontStore, backingStore);
    }
    
    @Test
    public void testLayerExists() {
        NestedBlobStore store = configureStore();
        Mockito.when(frontStore.layerExists("frontLayerName")).thenReturn(true);
        Mockito.when(frontStore.layerExists("backLayerName")).thenReturn(false);
        Mockito.when(backingStore.layerExists("backLayerName")).thenReturn(true);
        assertTrue(store.layerExists("frontLayerName"));
        assertTrue(store.layerExists("backLayerName"));
        assertFalse(store.layerExists("nonExistantLayerName"));
    }
    
    @Test 
    public void testGetParametersMapping() {
        NestedBlobStore store = configureStore();
        String layer="layer";
        store.getParametersMapping(layer);
        Mockito.verify(backingStore).getParametersMapping(layer);
        Mockito.verifyZeroInteractions(frontStore, backingStore);
    }

    @Test
    public void testAddBlobStoreListener() {
        NestedBlobStore store = configureStore();
        BlobStoreListener listener = new BlobStoreListener() {
            @Override
            public void tileStored(String layerName, String gridSetId, String blobFormat,
                    String parametersId, long x, long y, int z, long blobSize) {

            }

            @Override
            public void tileDeleted(String layerName, String gridSetId, String blobFormat,
                    String parametersId, long x, long y, int z, long blobSize) {

            }

            @Override
            public void tileUpdated(String layerName, String gridSetId, String blobFormat,
                    String parametersId, long x, long y, int z, long blobSize, long oldSize) {

            }

            @Override
            public void layerDeleted(String layerName) {

            }

            @Override
            public void layerRenamed(String oldLayerName, String newLayerName) {

            }

            @Override
            public void gridSubsetDeleted(String layerName, String gridSetId) {

            }

            @Override
            public void parametersDeleted(String layerName, String parametersId) {
                
            }
        };
        store.addListener(listener);
        Mockito.verify(backingStore).addListener(listener);
        Mockito.verifyZeroInteractions(frontStore);
        Mockito.verifyNoMoreInteractions(backingStore);
    }

    @Test
    public void testRemoveBlobStoreListener() {
        NestedBlobStore store = configureStore();
        BlobStoreListener listener = new BlobStoreListener() {
            @Override
            public void tileStored(String layerName, String gridSetId, String blobFormat,
                    String parametersId, long x, long y, int z, long blobSize) {

            }

            @Override
            public void tileDeleted(String layerName, String gridSetId, String blobFormat,
                    String parametersId, long x, long y, int z, long blobSize) {

            }

            @Override
            public void tileUpdated(String layerName, String gridSetId, String blobFormat,
                    String parametersId, long x, long y, int z, long blobSize, long oldSize) {

            }

            @Override
            public void layerDeleted(String layerName) {

            }

            @Override
            public void layerRenamed(String oldLayerName, String newLayerName) {

            }

            @Override
            public void gridSubsetDeleted(String layerName, String gridSetId) {

            }

            @Override
            public void parametersDeleted(String layerName, String parametersId) {
                
            }
        };
        store.removeListener(listener);
        Mockito.verify(backingStore).removeListener(listener);
        Mockito.verifyZeroInteractions(frontStore);
        Mockito.verifyNoMoreInteractions(backingStore);
    }

    private NestedBlobStore configureStore() {
        config = Mockito.mock(NestedBlobStoreConfig.class);
        Mockito.when(config.getBackingStore()).thenReturn(backingStore);
        Mockito.when(config.getFrontStore()).thenReturn(frontStore);
        NestedBlobStore store;
        store = new NestedBlobStore(config);
        return store;
    }

    private TileObject configureQuery() {
        TileObject to;
        to = queryTile(1, 2, 3);
        return to;
    }

    private TileObject queryTile(long x, long y, int z) {
        return queryTile(DEFAULT_LAYER, DEFAULT_GRIDSET, DEFAULT_FORMAT, x, y, z);
    }

    private TileObject queryTile(String layer, String gridset, String extension, long x, long y,
            int z) {
        return queryTile(layer, gridset, extension, x, y, z, (Map<String, String>) null);
    }

    private TileObject queryTile(String layer, String gridset, String extension, long x, long y,
            int z, Map<String, String> parameters) {

        String format;
        try {
            format = MimeType.createFromExtension(extension).getFormat();
        } catch (MimeException e) {
            throw Throwables.propagate(e);
        }

        TileObject tile = TileObject.createQueryTileObject(layer, new long[] { x, y, z }, gridset,
                format, parameters);
        return tile;
    }

    private TileRange tileRange(String layerName, String gridSetId, int zoomStart, int zoomStop,
            long[][] rangeBounds, MimeType mimeType, Map<String, String> parameters) {

        TileRange tileRange = new TileRange(layerName, gridSetId, zoomStart, zoomStop, rangeBounds,
                mimeType, parameters);
        return tileRange;
    }
}
