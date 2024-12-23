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
package org.geowebcache.blobstore.memory.distributed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.hazelcast.config.Config;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.geowebcache.storage.blobstore.memory.NullBlobStore;
import org.geowebcache.storage.blobstore.memory.distributed.HazelcastCacheProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This test class is used for testing {@link HazelcastCacheProvider} functionalities.
 *
 * @author Nicola Lagomarsini Geosolutions
 */
public class HazelcastCacheProviderTest {

    /** LOGGER */
    public static final Logger LOG = Logging.getLogger(HazelcastCacheProviderTest.class.getName());

    /** Name of the application context */
    public static final String APP_CONTEXT_FILENAME = "applicationContextTest.xml";

    /** Name of the bean associated to the first cache */
    public static final String CACHE_1_NAME = "HazelCastCacheProvider1";

    /** Name of the bean associated to the first cache */
    public static final String CACHE_2_NAME = "HazelCastCacheProvider2";

    /** Cache object 1 */
    private static HazelcastCacheProvider cache1;

    /** Cache object 2 */
    private static HazelcastCacheProvider cache2;

    /** First {@link MemoryBlobStore} instance used for tests */
    private static MemoryBlobStore mem1;

    /** Second {@link MemoryBlobStore} instance used for tests */
    private static MemoryBlobStore mem2;

    @BeforeClass
    public static void initialSetup() throws UnknownHostException {
        Config config = new Config();
        config.getMapConfig("default").setBackupCount(1).setAsyncBackupCount(0);
        config.setClusterName("gwc");
        TcpIpConfig tcpIpConfig = config.getNetworkConfig().getJoin().getTcpIpConfig();
        tcpIpConfig.setEnabled(true);
        tcpIpConfig.getMembers().add("localhost");
        HazelcastInstance h1 = Hazelcast.newHazelcastInstance(config);
        HazelcastInstance h2 = Hazelcast.newHazelcastInstance(config);
        // Create a nullblobstore to add to the memory blobstore
        NullBlobStore nbs = new NullBlobStore();

        mem1 = new MemoryBlobStore();
        mem1.setStore(nbs);
        cache1 = new HazelcastCacheProvider(h1.getMap("map1"), 16);
        mem1.setCacheProvider(cache1);

        mem2 = new MemoryBlobStore();
        mem2.setStore(nbs);
        cache2 = new HazelcastCacheProvider(h2.getMap("map1"), 16);
        mem2.setCacheProvider(cache2);

        // Ensure both the caches are available and immutable
        assertTrue(HazelcastCacheProviderTest.cache1.isAvailable());
        assertTrue(HazelcastCacheProviderTest.cache1.isImmutable());
        assertTrue(HazelcastCacheProviderTest.cache2.isAvailable());
        assertTrue(HazelcastCacheProviderTest.cache2.isImmutable());
    }

    @Test
    public void testTilePut() throws Exception {
        // Clearing cache
        cache1.clear();

        // Ensure that both caches are cleared
        assertEquals(0, cache1.getStatistics().getActualSize());
        assertEquals(0, cache2.getStatistics().getActualSize());

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = {1L, 2L, 3L};
        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject(
                "test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters, bytes);

        mem1.put(to);

        // Try to get the same TileObject
        TileObject to2 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        assertTrue(mem2.get(to2));

        // Checks on the format
        assertEquals(to.getBlobFormat(), to2.getBlobFormat());

        // Checks if the resources are equals
        try (InputStream is = to.getBlob().getInputStream();
                InputStream is2 = to2.getBlob().getInputStream()) {
            checkInputStreams(is, is2);
        }

        // Ensure Caches contain the result
        TileObject to3 = cache1.getTileObj(to);
        assertNotNull(to3);

        // Checks if the resources are equals
        try (InputStream is = to.getBlob().getInputStream();
                InputStream is3 = to3.getBlob().getInputStream()) {
            checkInputStreams(is, is3);
        }

        TileObject to4 = cache2.getTileObj(to);
        assertNotNull(to4);

        // Checks if the resources are equals
        try (InputStream is = to.getBlob().getInputStream();
                InputStream is4 = to4.getBlob().getInputStream()) {
            checkInputStreams(is, is4);
        }
    }

    @Test
    public void testTileDelete() throws Exception {
        // Clearing cache
        cache1.clear();

        // Ensure that both caches are cleared
        assertEquals(0, cache1.getStatistics().getActualSize());
        assertEquals(0, cache2.getStatistics().getActualSize());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "y");

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = {5L, 6L, 7L};
        TileObject to = TileObject.createCompleteTileObject(
                "test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters, bytes);

        mem1.put(to);

        // Try to get the same TileObject
        TileObject to2 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        assertTrue(mem2.get(to2));

        // Checks if the resources are equals
        try (InputStream is = to2.getBlob().getInputStream();
                InputStream is2 = bytes.getInputStream()) {
            checkInputStreams(is, is2);
        }

        // Delete TileObject
        TileObject to3 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        mem1.delete(to3);

        // Checks if the resource is not present
        TileObject to4 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        assertFalse(mem1.get(to4));
        assertFalse(mem2.get(to4));

        // Ensure that caches do not contain the TileObject
        TileObject to5 = cache1.getTileObj(to);
        assertNull(to5);
        TileObject to6 = cache2.getTileObj(to);
        assertNull(to6);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        // Blobstore destroy
        mem1.destroy();
        mem2.destroy();

        // Cache destruction
        cache1.destroy();
        cache2.destroy();

        // tear down hazelcast itself
        Hazelcast.shutdownAll();
    }

    /** Checks if the streams are equals, note that this method also closes the {@link InputStream} */
    @SuppressWarnings("PMD.UseTryWithResources") // resources not created here
    private void checkInputStreams(InputStream is, InputStream is2) throws IOException {
        try {
            // Ensure the two contents are equal
            assertTrue(IOUtils.contentEquals(is, is2));
        } finally {
            // Closing streams
            try {
                is.close();
            } catch (IOException e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);
                fail();
            }
            try {
                is2.close();
            } catch (IOException e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);
                fail();
            }
        }
    }
}
