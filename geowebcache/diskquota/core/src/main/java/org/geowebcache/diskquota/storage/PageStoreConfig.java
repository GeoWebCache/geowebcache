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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota.storage;

public class PageStoreConfig {

    private Integer cacheMemoryPercentAllowed;

    private Integer cacheSizeMB;

    /**
     * Percentage of the JVM heap size that can be used for the store's memory cache
     *
     * <p>This value and {@link #getCacheSizeMB()} are mutually exclusive. If both are present this value takes
     * precedence over {@link #getCacheSizeMB()}
     *
     * @return {@code null} if not set, an integer between 0 and 100 otherwise, representing the max percentage of JVM
     *     assigned heap size the store can use for its internal cache.
     */
    public Integer getCacheMemoryPercentAllowed() {
        return cacheMemoryPercentAllowed;
    }

    /**
     * Maximum size in MB that can be used for the store's memory cache
     *
     * <p>This value and {@link #getCacheMemoryPercentAllowed()} are mutually exclusive. If both are present
     * {@link #getCacheMemoryPercentAllowed()} value takes precedence over this value;
     *
     * @return {@code null} if not set, an integer between 0 and 100 otherwise, representing the max percentage of JVM
     *     assigned heap size the store can use for its internal cache.
     */
    public Integer getCacheSizeMB() {
        return cacheSizeMB;
    }
}
