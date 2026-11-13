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
 * <p>Copyright 2026
 */
package org.geowebcache.jetty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.hazelcast.HazelcastClientKeyValueStoreInfo;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.storage.memorycache.KeyValueStore;
import org.junit.ClassRule;
import org.junit.Test;

/** Runs GWC with {@code hazelcastLock} as lock provider, as {@code geowebcache.xml} would name it on a cluster. */
public class HazelcastLockIntegrationTest {

    private static final String CLUSTER = "gwc-lock-test";

    @ClassRule
    public static JettyRule jetty = new JettyRule(dir -> {}, HazelcastLockIntegrationTest::writeConfig);

    /** The template configuration, naming the Hazelcast locks. */
    private static void writeConfig(File cacheDir) throws IOException {
        String template = IOUtils.toString(XMLConfiguration.class.getResource("/geowebcache.xml"), UTF_8);
        String backendTimeout = "<backendTimeout>120</backendTimeout>";
        String config = template.replace(backendTimeout, backendTimeout + "<lockProvider>hazelcastLock</lockProvider>");
        FileUtils.writeStringToFile(new File(cacheDir, "geowebcache.xml"), config, UTF_8);
    }

    @Test
    @SuppressWarnings("PMD.UnusedLocalVariable") // the store is opened only to make the client lock on the cluster
    public void testLocksOnCluster() throws Exception {
        LockProvider locks = GeoWebCacheExtensions.bean(XMLConfiguration.class).getLockProvider();
        assertSame(GeoWebCacheExtensions.bean("hazelcastLock"), locks);

        HazelcastInstance member = Hazelcast.newHazelcastInstance(memberConfig());
        HazelcastClientKeyValueStoreInfo settings = new HazelcastClientKeyValueStoreInfo();
        settings.setClusterName(CLUSTER);
        settings.setMembers(
                "127.0.0.1:" + member.getCluster().getLocalMember().getAddress().getPort());
        // the store the cache would build, which makes the client connection lock on its cluster
        try (KeyValueStore store = settings.createStore()) {
            IMap<Object, Object> clusterLocks = member.getMap("gwc-tiles.locks");
            // the client connects in the background, locks stay local until then
            await().atMost(30, SECONDS).untilAsserted(() -> {
                Lock lock = locks.getLock("metatile");
                try {
                    assertTrue(clusterLocks.isLocked("metatile"));
                } finally {
                    lock.release();
                }
            });
            assertFalse(clusterLocks.isLocked("metatile"));
        } finally {
            member.shutdown();
        }
    }

    /** A member on the loopback interface only, finding no other member. */
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static Config memberConfig() {
        Config config = new Config().setClusterName(CLUSTER);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.setProperty("hazelcast.socket.bind.any", "false");
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getAutoDetectionConfig().setEnabled(false);
        join.getMulticastConfig().setEnabled(false);
        return config;
    }
}
