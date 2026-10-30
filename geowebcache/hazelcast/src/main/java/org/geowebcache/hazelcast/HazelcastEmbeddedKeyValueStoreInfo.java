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
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.storage.memorycache.KeyValueStore;
import org.geowebcache.storage.memorycache.KeyValueStoreInfo;

/** Settings of a store in a Hazelcast cluster this node is a member of; an absent element reads as its default. */
public class HazelcastEmbeddedKeyValueStoreInfo implements KeyValueStoreInfo {

    @Serial
    private static final long serialVersionUID = 1L;

    private String mapName;

    /** Name of the map holding the tiles, declared in {@code hazelcast.xml}; "gwc-tiles" by default. */
    public String getMapName() {
        return mapName == null ? HazelcastClientKeyValueStoreInfo.DEFAULT_MAP_NAME : mapName;
    }

    /** Sets the name of the map holding the tiles. */
    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    /**
     * Returns a store on the member shared by the application context, starting it first if needed.
     *
     * @throws IllegalStateException when the context has no {@link HazelcastEmbeddedMember}, or when
     *     {@code hazelcast.xml} is missing or does not declare the map
     */
    @Override
    public KeyValueStore createStore() {
        HazelcastEmbeddedMember member = GeoWebCacheExtensions.bean(HazelcastEmbeddedMember.class);
        if (member == null) throw new IllegalStateException("No embedded Hazelcast member in the application context");
        return member.createStore(this);
    }

    /** Equal when the effective settings are, an absent value matching its default. */
    @Override
    public boolean equals(Object o) {
        return o instanceof HazelcastEmbeddedKeyValueStoreInfo other
                && getMapName().equals(other.getMapName());
    }

    @Override
    public int hashCode() {
        return getMapName().hashCode();
    }
}
