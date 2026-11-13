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

import com.thoughtworks.xstream.XStream;
import org.geowebcache.config.Info;
import org.geowebcache.config.XMLConfigurationProvider;

/** Maps the Hazelcast backend settings to their {@code <backend class="...">} name in {@code geowebcache.xml}. */
public class HazelcastKeyValueStoreConfigProvider implements XMLConfigurationProvider {

    @Override
    public XStream getConfiguredXStream(XStream xs) {
        xs.alias("hazelcastClientKeyValueStore", HazelcastClientKeyValueStoreInfo.class);
        xs.alias("hazelcastEmbeddedKeyValueStore", HazelcastEmbeddedKeyValueStoreInfo.class);
        return xs;
    }

    /** Backend settings are saved with {@code <memoryCache>}, never as a standalone info. */
    @Override
    public boolean canSave(Info i) {
        return false;
    }
}
