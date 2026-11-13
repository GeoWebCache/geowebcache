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

import java.io.Serial;
import java.util.Objects;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.storage.memorycache.KeyValueStore;
import org.geowebcache.storage.memorycache.KeyValueStoreInfo;

/** Settings of a store in a Hazelcast cluster reached as a client; an absent element reads as its default. */
public class HazelcastClientKeyValueStoreInfo implements KeyValueStoreInfo {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String DEFAULT_CLUSTER_NAME = "gwc";

    static final String DEFAULT_MEMBERS = "localhost:5701";

    static final String DEFAULT_MAP_NAME = "gwc-tiles";

    private String clusterName;

    private String members;

    private String mapName;

    /** Name of the cluster to join, "gwc" by default, as in the embedded backend example. */
    public String getClusterName() {
        return clusterName == null ? DEFAULT_CLUSTER_NAME : clusterName;
    }

    /** Sets the name of the cluster to join. */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    /** Comma separated {@code host:port} list of members to connect to, "localhost:5701" by default. */
    public String getMembers() {
        return members == null ? DEFAULT_MEMBERS : members;
    }

    /** Sets the comma separated {@code host:port} list of members to connect to. */
    public void setMembers(String members) {
        this.members = members;
    }

    /** Name of the map holding the tiles, declared on the cluster; "gwc-tiles" by default. */
    public String getMapName() {
        return mapName == null ? DEFAULT_MAP_NAME : mapName;
    }

    /** Sets the name of the map holding the tiles. */
    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    /**
     * Returns a store on the client shared by the application context, connecting first if needed.
     *
     * @throws IllegalStateException when the context has no {@link HazelcastClientConnection}
     */
    @Override
    public KeyValueStore createStore() {
        HazelcastClientConnection connection = GeoWebCacheExtensions.bean(HazelcastClientConnection.class);
        if (connection == null) {
            throw new IllegalStateException("No Hazelcast client connection in the application context");
        }
        return connection.createStore(this);
    }

    /** Equal when the effective settings are, an absent value matching its default. */
    @Override
    public boolean equals(Object o) {
        return o instanceof HazelcastClientKeyValueStoreInfo other
                && getClusterName().equals(other.getClusterName())
                && getMembers().equals(other.getMembers())
                && getMapName().equals(other.getMapName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClusterName(), getMembers(), getMapName());
    }
}
