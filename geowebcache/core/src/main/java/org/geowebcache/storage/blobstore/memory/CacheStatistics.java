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

/**
 * This class is a container of all the Statistics of the {@link CacheProvider} object used by the
 * {@link MemoryBlobStore}.
 *
 * @author Nicola Lagomarsini, GeoSolutions
 */
public class CacheStatistics implements Serializable {

    /** serialVersionUID */
    @Serial
    private static final long serialVersionUID = -1049287017217353112L;

    /** Cache hit count */
    private long hitCount = 0;

    /** Cache miss count */
    private long missCount = 0;

    /** Cache eviction count */
    private long evictionCount = 0;

    /** Cache total request count (hit + miss) */
    private long totalCount = 0;

    /** Cache hit rate */
    private double hitRate = 0;

    /** Cache miss rate */
    private double missRate = 0;

    /** Cache current memory occupation */
    private double currentMemoryOccupation = 0;

    /** Cache total size */
    private long totalSize = 0;

    /** Cache actual size */
    private long actualSize = 0;

    public CacheStatistics() {}

    // Copy Constructor
    public CacheStatistics(CacheStatistics stats) {
        this.setEvictionCount(stats.getEvictionCount());
        this.setHitCount(stats.getHitCount());
        this.setMissCount(stats.getMissCount());
        this.setTotalCount(stats.getRequestCount());
        this.setHitRate(stats.getHitRate());
        this.setMissRate(stats.getMissRate());
        this.setCurrentMemoryOccupation(stats.getCurrentMemoryOccupation());
        this.setActualSize(stats.getActualSize());
        this.setTotalSize(stats.getTotalSize());
    }

    /** @return the cache hit count */
    public long getHitCount() {
        return hitCount;
    }

    /** Setter for cache hit count */
    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }

    /** @return the cache miss count */
    public long getMissCount() {
        return missCount;
    }

    /** Setter for cache miss count */
    public void setMissCount(long missCount) {
        this.missCount = missCount;
    }

    /** @return the cache eviction count */
    public long getEvictionCount() {
        return evictionCount;
    }

    /** Setter for cache eviction count */
    public void setEvictionCount(long evictionCount) {
        this.evictionCount = evictionCount;
    }

    /** @return the cache total request count */
    public long getRequestCount() {
        return totalCount;
    }

    /** Setter for cache total count */
    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    /** @return the cache hit rate */
    public double getHitRate() {
        return hitRate;
    }

    /** Setter for cache hit rate */
    public void setHitRate(double hitRate) {
        this.hitRate = hitRate;
    }

    /** @return the cache miss rate */
    public double getMissRate() {
        return missRate;
    }

    /** Setter for cache miss rate */
    public void setMissRate(double missRate) {
        this.missRate = missRate;
    }

    /** @return the cache current memory occupation */
    public double getCurrentMemoryOccupation() {
        return currentMemoryOccupation;
    }

    /** Setter for cache memory occupation */
    public void setCurrentMemoryOccupation(double currentMemoryOccupation) {
        this.currentMemoryOccupation = currentMemoryOccupation;
    }

    /** @return the cache current total size */
    public long getTotalSize() {
        return totalSize;
    }

    /** Setter for cache total size */
    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    /** @return the cache current actual size */
    public long getActualSize() {
        return actualSize;
    }

    /** Setter for cache actual size */
    public void setActualSize(long actualSize) {
        this.actualSize = actualSize;
    }
}
