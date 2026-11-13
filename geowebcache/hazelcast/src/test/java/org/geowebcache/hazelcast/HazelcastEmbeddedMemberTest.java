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
package org.geowebcache.hazelcast;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.geowebcache.hazelcast.HazelcastTestMembers.CLUSTER;
import static org.geowebcache.hazelcast.HazelcastTestMembers.NO_FILE;
import static org.geowebcache.hazelcast.HazelcastTestMembers.freePort;
import static org.geowebcache.hazelcast.HazelcastTestMembers.xml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Objects;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.storage.memorycache.AbstractKeyValueStoreTest;
import org.geowebcache.storage.memorycache.KeyValueStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Runs the store on a member embedded in the test JVM, configured as an admin would in {@code hazelcast.xml}. */
public class HazelcastEmbeddedMemberTest extends AbstractKeyValueStoreTest {

    private static final String MEMBER_XML =
            """
            <hazelcast xmlns="http://www.hazelcast.com/schema/config">
              <cluster-name>%s</cluster-name>
              <properties>
                <property name="hazelcast.phone.home.enabled">false</property>
                <property name="hazelcast.socket.bind.any">false</property>
              </properties>
              <network>
                <port auto-increment="false">%d</port>
                <join>
                  <auto-detection enabled="false"/>
                  <multicast enabled="false"/>
                </join>
                <interfaces enabled="true">
                  <interface>127.0.0.1</interface>
                </interfaces>
              </network>
              <map name="gwc-tiles">
                <eviction eviction-policy="LRU" max-size-policy="USED_HEAP_SIZE" size="64"/>
              </map>
            </hazelcast>
            """;

    private static final byte[] TILE = {1};

    private static HazelcastEmbeddedMember backend;

    @BeforeClass
    public static void createBackend() {
        backend = new HazelcastEmbeddedMember(xml(MEMBER_XML.formatted(CLUSTER, freePort())));
    }

    @AfterClass
    public static void destroyBackend() {
        backend.destroy();
    }

    @Override
    protected KeyValueStore createStore() {
        KeyValueStore created = backend.createStore(new HazelcastEmbeddedKeyValueStoreInfo());
        // the member joins in the background, calls fail until then
        await().atMost(30, SECONDS).ignoreExceptions().untilAsserted(created::clear);
        return created;
    }

    @Test
    public void testStoppedMemberStartedAgain() {
        int port = freePort();
        String memberXml = MEMBER_XML.formatted(CLUSTER, port);
        HazelcastEmbeddedMember own = new HazelcastEmbeddedMember(xml(memberXml));
        try (KeyValueStore restarted = own.createStore(new HazelcastEmbeddedKeyValueStoreInfo())) {
            await().atMost(30, SECONDS).ignoreExceptions().untilAsserted(() -> restarted.put("key", TILE, 300));

            // as an out of memory error or a cluster shutdown would
            Hazelcast.getAllHazelcastInstances().stream()
                    .filter(m -> m.getCluster().getLocalMember().getAddress().getPort() == port)
                    .forEach(m -> m.getLifecycleService().terminate());
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> restarted.get("key"));
            assertThat(e.getMessage(), containsString("stopped"));

            // the same store works again once the new member has joined
            await().atMost(30, SECONDS).ignoreExceptions().untilAsserted(() -> restarted.put("key", TILE, 300));
        } finally {
            own.destroy();
        }
    }

    @Test
    public void testFailedMemberNotStartedAgainAtOnce() throws Exception {
        // the member cannot bind its port, so every start fails at once
        ServerSocket taken = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        HazelcastEmbeddedMember own =
                new HazelcastEmbeddedMember(xml(MEMBER_XML.formatted(CLUSTER, taken.getLocalPort())));
        try (taken;
                KeyValueStore store = own.createStore(new HazelcastEmbeddedKeyValueStoreInfo())) {
            // the first failure starts a new member right away
            Throwable first = await().atMost(30, SECONDS).until(() -> failedStart(store), Objects::nonNull);
            Throwable second = await().atMost(30, SECONDS)
                    .until(() -> failedStart(store), cause -> cause != null && cause != first);

            // within the interval the same failure is reported, as lock calls would otherwise start a member each
            assertSame(second, failedStart(store));
        } finally {
            own.destroy();
        }
    }

    /** The cause of the failed start {@code store} reports, null while the member is still joining. */
    private static Throwable failedStart(KeyValueStore store) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> store.get("key"));
        return e.getMessage().contains("failed to start") ? e.getCause() : null;
    }

    @Test
    public void testMissingFile() {
        HazelcastEmbeddedMember missing = new HazelcastEmbeddedMember(NO_FILE);
        HazelcastEmbeddedKeyValueStoreInfo settings = new HazelcastEmbeddedKeyValueStoreInfo();

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> missing.createStore(settings));

        assertThat(e.getMessage(), containsString("hazelcast.xml"));
    }

    @Test
    public void testLockProviderLocksOnMember() throws Exception {
        int port = freePort();
        HazelcastEmbeddedMember own = new HazelcastEmbeddedMember(xml(MEMBER_XML.formatted(CLUSTER, port)));
        HazelcastLockProvider locks = new HazelcastLockProvider();
        own.setLockProvider(locks);
        try (KeyValueStore store = own.createStore(new HazelcastEmbeddedKeyValueStoreInfo())) {
            await().atMost(30, SECONDS).ignoreExceptions().untilAsserted(store::clear);
            HazelcastInstance member = Hazelcast.getAllHazelcastInstances().stream()
                    .filter(m -> m.getCluster().getLocalMember().getAddress().getPort() == port)
                    .findFirst()
                    .orElseThrow();

            Lock lock = locks.getLock("key");
            assertTrue(member.getMap("gwc-tiles.locks").isLocked("key"));
            lock.release();
            assertFalse(member.getMap("gwc-tiles.locks").isLocked("key"));
        } finally {
            own.destroy();
        }
    }

    @Test
    public void testUndeclaredMap() {
        HazelcastEmbeddedKeyValueStoreInfo settings = new HazelcastEmbeddedKeyValueStoreInfo();
        settings.setMapName("undeclared");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> backend.createStore(settings));

        assertThat(e.getMessage(), containsString("undeclared"));
    }
}
