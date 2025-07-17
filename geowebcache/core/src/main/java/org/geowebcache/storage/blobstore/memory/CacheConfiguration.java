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
 * <p>Copyright 2019
 */
package org.geowebcache.storage.blobstore.memory;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * This class contains the configuration for the {@link CacheProvider} object to use.
 *
 * @author Nicola Lagomarsini Geosolutions
 */
public class CacheConfiguration implements Serializable {

    /** serialVersionUID */
    @Serial
    private static final long serialVersionUID = 3875121032331372267L;

    /** Evction policy */
    public enum EvictionPolicy {
        LRU,
        LFU,
        EXPIRE_AFTER_WRITE,
        EXPIRE_AFTER_ACCESS,
        NULL;

        public static EvictionPolicy getEvictionPolicy(String policy) {
            if (policy == null || policy.isEmpty()) {
                return NULL;
            }
            return valueOf(policy);
        }
    }

    /** Default value for the Cache memory limit */
    public static final long DEFAULT_MEMORY_LIMIT = 16; // 16Mb

    /** Default value for the cache concurrency level */
    public static final int DEFAULT_CONCURRENCY_LEVEL = 4;

    /** Default value for the eviction time (s) */
    public static final long DEFAULT_EVICTION_TIME = 2 * 60; // 2 minutes

    /** Default value for the eviction policy */
    public static final EvictionPolicy DEFAULT_EVICTION_POLICY = EvictionPolicy.NULL;

    /** Parameter associated to the Cache memory limit */
    private long hardMemoryLimit = DEFAULT_MEMORY_LIMIT;

    /** Cache eviction policy */
    private EvictionPolicy policy = DEFAULT_EVICTION_POLICY;

    /** Parameter associated to the Cache concurrency level */
    private int concurrencyLevel = DEFAULT_CONCURRENCY_LEVEL;

    /** Start Value */
    private long evictionTime = DEFAULT_EVICTION_TIME;

    /** @return the current cache memory limit */
    public long getHardMemoryLimit() {
        return hardMemoryLimit;
    }

    /** Sets the cache memory limit */
    public void setHardMemoryLimit(long hardMemoryLimit) {
        this.hardMemoryLimit = hardMemoryLimit;
    }

    /** @return The cache eviction policy */
    public EvictionPolicy getPolicy() {
        return policy;
    }

    /** Sets the Cache eviction policy */
    public void setPolicy(EvictionPolicy policy) {
        this.policy = policy;
    }

    /** @return the cache concurrency level */
    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    /** Sets the cache concurrency level */
    public void setConcurrencyLevel(int concurrencyLevel) {
        this.concurrencyLevel = concurrencyLevel;
    }

    /** @return the cache eviction time */
    public long getEvictionTime() {
        return evictionTime;
    }

    /** Sets the cache eviction time */
    public void setEvictionTime(long evictionTime) {
        this.evictionTime = evictionTime;
    }

    @Override
    public boolean equals(Object obj) {
        // Ensure that the internal objects are equals
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof CacheConfiguration)) {
            return false;
        }
        CacheConfiguration config = (CacheConfiguration) obj;
        if (this.concurrencyLevel != config.concurrencyLevel) {
            return false;
        } else if (!(this.policy == config.policy)) {
            return false;
        } else if (this.hardMemoryLimit != config.hardMemoryLimit) {
            return false;
        } else if (this.evictionTime != config.evictionTime) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hardMemoryLimit, policy, concurrencyLevel, evictionTime);
    }
}
