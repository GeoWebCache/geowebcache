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
package org.geowebcache.storage.memorycache;

import java.io.Serializable;

/**
 * Settings of one cache backend, persisted as the {@code <backend>} of {@code <memoryCache>}, and the factory of its
 * {@link KeyValueStore}; each implementation needs an XStream alias, registered by its module.
 */
public interface KeyValueStoreInfo extends Serializable {

    /** Returns a new store for these settings; the caller closes it. */
    KeyValueStore createStore();
}
