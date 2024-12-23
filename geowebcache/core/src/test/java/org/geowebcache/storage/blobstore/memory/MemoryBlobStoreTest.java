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
 */
package org.geowebcache.storage.blobstore.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageBrokerTest;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.geowebcache.storage.blobstore.memory.guava.GuavaCacheProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This test class is used for testing {@link org.geowebcache.storage.blobstore.memory.MemoryBlobStore} functionality
 *
 * @author Nicola Lagomarsini Geosolutions
 */
public class MemoryBlobStoreTest {

    /** LOGGER */
    public static final Logger log = Logging.getLogger(MemoryBlobStoreTest.class.getName());

    public static final String TEST_BLOB_DIR_NAME = "gwcTestBlobs";

    /** Cache object */
    private CacheProvider cache;

    private MemoryBlobStore mbs;
    private NullBlobStore nbs;
    private BlobStore fbs;

    @After
    public void destroyBlobStores() {
        if (mbs != null) {
            mbs.destroy();
        }
        if (nbs != null) {
            nbs.destroy();
        }
        if (fbs != null) {
            fbs.destroy();
        }
    }

    @Before
    public void initCache() {
        // Initial cache configuration
        cache = new GuavaCacheProvider(new CacheConfiguration());
    }

    @Test
    public void testNullStore() throws Exception {
        // Add a nullblobstore to the memory blobstore
        nbs = new NullBlobStore();
        cache.clear();

        mbs = new MemoryBlobStore();
        mbs.setStore(nbs);
        mbs.setCacheProvider(cache);

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = {1L, 2L, 3L};
        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject(
                "test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters, bytes);

        mbs.put(to);
        // Try to get the same TileObject
        TileObject to2 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        mbs.get(to2);

        // Checks on the format
        assertEquals(to.getBlobFormat(), to2.getBlobFormat());

        // Checks if the resources are equals
        try (InputStream is = to.getBlob().getInputStream();
                InputStream is2 = to2.getBlob().getInputStream()) {
            checkInputStreams(is, is2);
        }

        // Ensure Cache contains the result
        TileObject to3 = cache.getTileObj(to);
        assertNotNull(to3);
        assertEquals(to.getBlobFormat(), to3.getBlobFormat());

        // Checks if the resources are equals
        try (InputStream is = to.getBlob().getInputStream();
                InputStream is3 = to3.getBlob().getInputStream()) {
            checkInputStreams(is, is3);
        }

        // Ensure that NullBlobStore does not contain anything
        assertFalse(nbs.get(to));
    }

    @Test
    public void testTilePut() throws Exception {
        // Add a fileblobstore to the memory blobstore
        fbs = setup();
        cache.clear();

        mbs = new MemoryBlobStore();
        mbs.setStore(fbs);
        mbs.setCacheProvider(cache);

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = {1L, 2L, 3L};
        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject(
                "test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters, bytes);

        mbs.put(to);

        // Try to get the same TileObject
        TileObject to2 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        mbs.get(to2);

        // Checks on the format
        assertEquals(to.getBlobFormat(), to2.getBlobFormat());

        // Checks if the resources are equals
        try (InputStream is = to.getBlob().getInputStream();
                InputStream is2 = to2.getBlob().getInputStream()) {
            checkInputStreams(is, is2);
        }

        // Ensure Cache contains the result
        TileObject to3 = cache.getTileObj(to);
        assertNotNull(to3);
        assertEquals(to.getBlobFormat(), to3.getBlobFormat());

        // Checks if the resources are equals
        try (InputStream is = to.getBlob().getInputStream();
                InputStream is3 = to3.getBlob().getInputStream()) {
            checkInputStreams(is, is3);
        }
    }

    @Test
    public void testTileDelete() throws Exception {
        // Add a fileblobstore to the memory blobstore
        fbs = setup();
        cache.clear();

        mbs = new MemoryBlobStore();
        mbs.setStore(fbs);
        mbs.setCacheProvider(cache);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "ø");

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = {5L, 6L, 7L};
        TileObject to = TileObject.createCompleteTileObject(
                "test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters, bytes);

        mbs.put(to);

        // Try to get the same TileObject
        TileObject to2 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        mbs.get(to2);

        // Checks if the resources are equals
        try (InputStream is = to2.getBlob().getInputStream();
                InputStream is2 = bytes.getInputStream()) {
            checkInputStreams(is, is2);
        }

        // Delete TileObject
        TileObject to3 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        mbs.delete(to3);

        // Checks if the resource is not present
        TileObject to4 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        assertFalse(mbs.get(to4));

        // Ensure that cache does not contain the TileObject
        TileObject to5 = cache.getTileObj(to);
        assertNull(to5);
    }

    @Test
    public void testLastModifiedFromFilesystem() throws Exception {
        // Add a fileblobstore to the memory blobstore
        fbs = setup();
        cache.clear();

        mbs = new MemoryBlobStore();
        mbs.setStore(fbs);
        mbs.setCacheProvider(cache);

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = {1L, 2L, 3L};
        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject(
                "test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters, bytes);

        mbs.put(to);
        mbs.clear();

        // Try to get the same TileObject twice with a cache cleanup in the middle
        TileObject to2 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        mbs.get(to2);
        mbs.clear();
        // wait a second to ensure we are not getting the same tile because the machine is just that
        // fast
        Thread.sleep(1000);
        TileObject to3 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        mbs.get(to3);

        // check the last modified is the same
        assertEquals(to2.getCreated(), to3.getCreated());
    }

    /**
     * * Private method for creating a {@link FileBlobStore}
     *
     * @return a new FileBlobStore
     */
    private BlobStore setup() throws Exception {
        File fh = new File(StorageBrokerTest.findTempDir() + File.separator + TEST_BLOB_DIR_NAME);

        if (fh.exists()) {
            FileUtils.deleteDirectory(fh);
        }
        if (!fh.exists()) {
            Files.createDirectories(fh.toPath());
        }

        return new FileBlobStore(StorageBrokerTest.findTempDir() + File.separator + TEST_BLOB_DIR_NAME);
    }

    /** Checks if the streams are equals, note that this method also closes the {@link InputStream} */
    @SuppressWarnings("PMD.UseTryWithResources") // provided as method params, handle in java 11
    private void checkInputStreams(InputStream is, InputStream is2) throws IOException {
        try {
            assertTrue(IOUtils.contentEquals(is, is2));
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                fail();
            }
            try {
                is2.close();
            } catch (IOException e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                fail();
            }
        }
    }
}
