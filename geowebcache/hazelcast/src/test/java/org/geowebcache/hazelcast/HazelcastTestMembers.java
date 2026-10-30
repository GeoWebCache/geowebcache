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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.stream.IntStream;
import org.geowebcache.config.ConfigurationResourceProvider;
import org.geowebcache.config.MockConfigurationResourceProvider;

/** Members started in the test JVM on loopback, at fixed ports so a restarted member is found again by the client. */
final class HazelcastTestMembers {

    static final String CLUSTER = "gwc-hazelcast-test";

    /** A Hazelcast configuration file that does not exist. */
    static final ConfigurationResourceProvider NO_FILE = new MockConfigurationResourceProvider(null) {
        @Override
        public boolean hasInput() {
            return false;
        }
    };

    private HazelcastTestMembers() {}

    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Starts a member on {@code port}, joining the members at {@code ports} as well. */
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP") // loopback only, test members never join a real cluster
    static HazelcastInstance start(int port, int... ports) {
        Config config = new Config().setClusterName(CLUSTER);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        // loopback only, otherwise the member may announce a VPN or virtual adapter address the client cannot reach
        config.setProperty("hazelcast.socket.bind.any", "false");
        NetworkConfig network = config.getNetworkConfig();
        network.setPort(port).setPortAutoIncrement(false);
        network.getInterfaces().setEnabled(true).addInterface("127.0.0.1");

        JoinConfig join = network.getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        IntStream.of(ports)
                .forEach(peer -> join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1:" + peer));
        return Hazelcast.newHazelcastInstance(config);
    }

    /** A Hazelcast configuration file holding {@code content}. */
    static ConfigurationResourceProvider xml(String content) {
        return new MockConfigurationResourceProvider(() -> new ByteArrayInputStream(content.getBytes(UTF_8)));
    }

    /** Client settings reaching the members at {@code ports}. */
    static HazelcastClientKeyValueStoreInfo clientSettings(int... ports) {
        HazelcastClientKeyValueStoreInfo settings = new HazelcastClientKeyValueStoreInfo();
        settings.setClusterName(CLUSTER);
        settings.setMembers(IntStream.of(ports).mapToObj(p -> "127.0.0.1:" + p).collect(joining(",")));
        return settings;
    }
}
