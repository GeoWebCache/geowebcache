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
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */
package org.geowebcache.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.config.FileBlobStoreInfo;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.CompositeBlobStore.LiveStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatcher;

import com.google.common.base.Throwables;

public class CompositeBlobStoreTest {

    private static final String DEFAULT_GRIDSET = "EPSG:4326";

    private static final String DEFAULT_FORMAT = "png";

    private static final String DEFAULT_LAYER = "topp:states";

    private static class NotEq<T> extends ArgumentMatcher<T> {

        private T val;

        public NotEq(T val) {
            this.val = val;
        }

        @Override
        public boolean matches(Object argument) {
            return !val.equals(argument);
        }

    }

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Rule
    public ExpectedException ex = ExpectedException.none();

    TileLayerDispatcher layers;

    DefaultStorageFinder defaultStorageFinder;

    XMLConfiguration configuration;

    List<BlobStoreInfo> configs;

    private CompositeBlobStore store;

    private TileLayer defaultLayer;

    @Before
    public void setup() throws Exception {

        layers = mock(TileLayerDispatcher.class);
        defaultStorageFinder = mock(DefaultStorageFinder.class);
        configuration = mock(XMLConfiguration.class);

        configs = new LinkedList<>();
        when(configuration.getBlobStores()).thenReturn(configs);

        when(defaultStorageFinder.getDefaultPath()).thenReturn(
                tmpFolder.getRoot().getAbsolutePath());

        defaultLayer = mock(TileLayer.class);
        when(layers.getTileLayer(eq(DEFAULT_LAYER))).thenReturn(defaultLayer);
        when(layers.getTileLayer((String) argThat(new NotEq<>(DEFAULT_LAYER)))).thenThrow(
                new GeoWebCacheException("layer not found"));
    }

    private CompositeBlobStore create() throws StorageException, ConfigurationException {
        return new CompositeBlobStore(layers, defaultStorageFinder, configuration);
    }

    @Test
    public void noStoresDefinedCreatesLegacyDefaultStore() throws Exception {
        store = create();

        assertEquals(1, store.blobStores.size());
        LiveStore liveStore = store.blobStores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID);
        assertNotNull(liveStore);
        assertTrue(liveStore.config instanceof FileBlobStoreInfo);
        FileBlobStoreInfo config = (FileBlobStoreInfo) liveStore.config;
        assertTrue(config.isEnabled());
        assertTrue(config.isDefault());
        assertEquals(tmpFolder.getRoot().getAbsolutePath(), config.getBaseDirectory());
    }

    @Test
    public void noExplicitDefaultCreatesLegacyDefaultStore() throws Exception {
        final boolean isDefault = false;
        configs.add(config("store1", isDefault, true, tmpFolder.newFolder().getAbsolutePath(), 1024));
        configs.add(config("store2", isDefault, true, tmpFolder.newFolder().getAbsolutePath(), 2048));

        store = create();

        Map<String, LiveStore> stores = store.blobStores;
        assertEquals(3, stores.size());
        LiveStore defaultStore = stores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID);
        assertNotNull(defaultStore);
        assertNotEquals(configs.get(0), defaultStore.config);
        assertNotEquals(configs.get(1), defaultStore.config);
    }

    @Test
    public void duplicateDefaultStoreFails() throws Exception {

        final boolean isDefault = true;
        configs.add(config("store1", isDefault, true, tmpFolder.newFolder().getAbsolutePath(), 1024));
        configs.add(config("store2", isDefault, true, tmpFolder.newFolder().getAbsolutePath(), 2048));

        ex.expect(ConfigurationException.class);
        ex.expectMessage("Duplicate default blob store");
        store = create();
    }

    @Test
    public void nullStoreIdFails() throws Exception {
        String id = null;
        configs.add(config(id, true, true, tmpFolder.newFolder().getAbsolutePath(), 1024));

        ex.expect(ConfigurationException.class);
        ex.expectMessage("No id provided for blob store");
        store = create();
    }

    @Test
    public void duplicateStoreIdFails() throws Exception {
        String id = "ImDuplicate";
        configs.add(config(id, true, true, tmpFolder.newFolder().getAbsolutePath(), 1024));
        configs.add(config(id, true, true, tmpFolder.newFolder().getAbsolutePath(), 1024));

        ex.expect(ConfigurationException.class);
        ex.expectMessage("Duplicate blob store id");
        store = create();
    }

    @Test
    public void defaultAndDisaledFails() throws Exception {
        boolean isDefault = true;
        boolean enabled = false;
        configs.add(config("storeId", isDefault, enabled, tmpFolder.newFolder().getAbsolutePath(),
                1024));

        ex.expect(ConfigurationException.class);
        ex.expectMessage("The default blob store can't be disabled");
        store = create();
    }

    @Test
    public void disabledStoreHasNoLiveInstance() throws Exception {
        boolean enabled = false;
        configs.add(config("storeId", false, enabled, tmpFolder.newFolder().getAbsolutePath(), 1024));

        store = create();
        assertNotNull(store.blobStores.get("storeId"));
        assertNull(store.blobStores.get("storeId").liveInstance);
    }

    @Test
    public void reservedDefaultIdInvalidInConfig() throws Exception {
        String id = CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID;
        configs.add(config(id, true, true, tmpFolder.newFolder().getAbsolutePath(), 1024));

        ex.expect(ConfigurationException.class);
        ex.expectMessage(id + " is a reserved identifier");
        store = create();
    }

    @Test
    public void configuredDefaultRespectedAndNoLegacyDefaultCreated() throws Exception {
        configs.add(config("some-other", false /* isDefault */, true, tmpFolder.newFolder()
                .getAbsolutePath(), 1024));
        FileBlobStoreInfo defaultStore = config("default-store", true, true, tmpFolder
                .newFolder().getAbsolutePath(), 1024);
        configs.add(defaultStore);

        store = create();
        // defaultStore is cached twice, with its own id for when layers refers to it explicitly,
        // and as CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID for layers that do not specify a blob
        // store
        assertSame(defaultStore,
                store.blobStores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID).config);
        assertSame(defaultStore, store.blobStores.get("default-store").config);
        assertEquals(3, store.blobStores.size());
    }

    @Test
    public void getTileInvalidBlobStore() throws Exception {
        configs.add(config("default", true, true, tmpFolder.newFolder().getAbsolutePath(), 1024));

        store = create();

        when(defaultLayer.getBlobStoreId()).thenReturn("nonExistentStore");
        ex.expect(StorageException.class);
        ex.expectMessage("No BlobStore with id 'nonExistentStore' found");
        store.get(queryTile(0, 0, 0));
    }

    @Test
    public void getTileDefaultsToDefaultBlobStore() throws Exception {
        store = create();

        LiveStore liveStore = store.blobStores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID);
        liveStore.liveInstance = spy(liveStore.liveInstance);

        when(defaultLayer.getBlobStoreId()).thenReturn(null);
        TileObject tile = queryTile(0, 0, 0);
        store.get(tile);
        verify(liveStore.liveInstance).get(tile);
    }

    @Test
    public void getTileInvalidLayer() throws Exception {
        store = create();

        LiveStore liveStore = store.blobStores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID);
        liveStore.liveInstance = spy(liveStore.liveInstance);

        when(defaultLayer.getBlobStoreId()).thenReturn(null);
        TileObject tile = queryTile("someLayer", DEFAULT_GRIDSET, DEFAULT_FORMAT, 0, 0, 0);

        ex.expect(StorageException.class);
        ex.expectMessage("layer not found");
        store.get(tile);
    }

    @Test
    public void getTileDisabledStore() throws Exception {
        boolean isEnabled = false;
        configs.add(config("store1", false, isEnabled, tmpFolder.newFolder().getAbsolutePath(),
                1024));

        store = create();

        when(defaultLayer.getBlobStoreId()).thenReturn("store1");
        TileObject tile = queryTile(0, 0, 0);

        ex.expect(StorageException.class);
        ex.expectMessage("Attempted to use a blob store that's disabled");
        store.get(tile);
    }

    private FileBlobStoreInfo config(String id, boolean isDefault, boolean isEnabled,
            String baseDirectory, int fileSystemBlockSize) {
        FileBlobStoreInfo c = new FileBlobStoreInfo(id);
        c.setDefault(isDefault);
        c.setEnabled(isEnabled);
        c.setBaseDirectory(baseDirectory);
        c.setFileSystemBlockSize(fileSystemBlockSize);
        return c;
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

}
