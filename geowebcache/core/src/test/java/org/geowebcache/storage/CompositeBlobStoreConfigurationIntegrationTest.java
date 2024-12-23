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
 * <p>Copyright 2018
 */
package org.geowebcache.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.FileBlobStoreInfo;
import org.geowebcache.config.GWCConfigIntegrationTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests integration between {@link CompositeBlobStore}, {@link org.geowebcache.storage.BlobStoreAggregator}, and
 * {@link org.geowebcache.config.BlobStoreConfigurationListener} events.
 *
 * <p>Specifically, tests that CompositeBlobStore and BlobStoreAggregator remain in sync across configuration changes.
 */
public class CompositeBlobStoreConfigurationIntegrationTest extends GWCConfigIntegrationTest {

    CompositeBlobStore compositeBlobStore;

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Rule
    public MockWepAppContextRule context = new MockWepAppContextRule();

    @Override
    @Before
    public void setUpTest() throws Exception {
        super.setUpTest();
        compositeBlobStore = new CompositeBlobStore(
                tileLayerDispatcher,
                new DefaultStorageFinder(context.getContextProvider()),
                testSupport.getServerConfiguration(),
                blobStoreAggregator);
    }

    private FileBlobStoreInfo createInfo(
            String id, boolean isDefault, boolean isEnabled, String baseDirectory, int fileSystemBlockSize) {
        FileBlobStoreInfo c = new FileBlobStoreInfo(id);
        c.setDefault(isDefault);
        c.setEnabled(isEnabled);
        c.setBaseDirectory(baseDirectory);
        c.setFileSystemBlockSize(fileSystemBlockSize);
        return c;
    }

    @Test
    public void testAdd() throws IOException {
        assertFalse(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));

        BlobStoreInfo info = createInfo(
                "newFileBlobStore", false, true, tmpFolder.newFolder().getAbsolutePath(), 1024);
        blobStoreAggregator.addBlobStore(info);

        assertTrue(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));
    }

    @Test
    public void testAddDefault() throws IOException {
        assertFalse(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));

        BlobStoreInfo info =
                createInfo("newFileBlobStore", true, true, tmpFolder.newFolder().getAbsolutePath(), 1024);
        blobStoreAggregator.addBlobStore(info);

        assertTrue(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));
        assertEquals(
                compositeBlobStore.blobStores.get("newFileBlobStore"),
                compositeBlobStore.blobStores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID));
    }

    @Test
    public void testModify() throws IOException, GeoWebCacheException {
        testAdd();

        BlobStore oldLiveInstance = compositeBlobStore.blobStores.get("newFileBlobStore").liveInstance;

        FileBlobStoreInfo info = (FileBlobStoreInfo) blobStoreAggregator.getBlobStore("newFileBlobStore");
        info.setFileSystemBlockSize(2048);

        blobStoreAggregator.modifyBlobStore(info);
        assertTrue(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));
        assertNotEquals(oldLiveInstance, compositeBlobStore.blobStores.get("newFileBlobStore").liveInstance);
    }

    @Test
    public void testModifyDefault() throws IOException, GeoWebCacheException {
        testAddDefault();

        BlobStore oldLiveInstance = compositeBlobStore.blobStores.get("newFileBlobStore").liveInstance;

        FileBlobStoreInfo info = (FileBlobStoreInfo) blobStoreAggregator.getBlobStore("newFileBlobStore");
        info.setFileSystemBlockSize(2048);

        blobStoreAggregator.modifyBlobStore(info);
        assertTrue(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));
        assertNotEquals(oldLiveInstance, compositeBlobStore.blobStores.get("newFileBlobStore").liveInstance);
        assertEquals(
                compositeBlobStore.blobStores.get("newFileBlobStore"),
                compositeBlobStore.blobStores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID));
    }

    @Test
    public void testModifySetDefault() throws IOException, GeoWebCacheException {
        testAdd();

        FileBlobStoreInfo info = (FileBlobStoreInfo) blobStoreAggregator.getBlobStore("newFileBlobStore");
        info.setDefault(true);

        blobStoreAggregator.modifyBlobStore(info);
        assertTrue(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));
        assertEquals(
                compositeBlobStore.blobStores.get("newFileBlobStore"),
                compositeBlobStore.blobStores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID));
    }

    @Test
    public void testRename() throws IOException, GeoWebCacheException {
        testAdd();

        blobStoreAggregator.renameBlobStore("newFileBlobStore", "renamedFileBlobStore");
        assertFalse(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));
        assertTrue(compositeBlobStore.blobStores.containsKey("renamedFileBlobStore"));
    }

    @Test
    public void testRenameDefault() throws IOException, GeoWebCacheException {
        testAddDefault();

        blobStoreAggregator.renameBlobStore("newFileBlobStore", "renamedFileBlobStore");
        assertFalse(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));
        assertTrue(compositeBlobStore.blobStores.containsKey("renamedFileBlobStore"));
        assertEquals(
                compositeBlobStore.blobStores.get("renamedFileBlobStore"),
                compositeBlobStore.blobStores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID));
    }

    @Test
    public void testRemove() throws IOException {
        testAdd();

        blobStoreAggregator.removeBlobStore("newFileBlobStore");
        assertFalse(compositeBlobStore.blobStores.containsKey("newFileBlobStore"));
    }
}
