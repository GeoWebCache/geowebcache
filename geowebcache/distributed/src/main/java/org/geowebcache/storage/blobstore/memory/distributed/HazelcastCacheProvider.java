/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.geowebcache.storage.blobstore.memory.distributed;

import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration.EvictionPolicy;
import org.geowebcache.storage.blobstore.memory.CacheProvider;
import org.geowebcache.storage.blobstore.memory.CacheStatistics;
import org.geowebcache.storage.blobstore.memory.guava.GuavaCacheProvider;
import org.springframework.beans.factory.DisposableBean;

import com.hazelcast.core.IMap;
import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;

/**
 * This class is an implementation of the {@link CacheProvider} interface for a distributed configuration using Hazelcast. This class requires a
 * configuration at the GWC startup. The Hazelcast instance used is returned by the {@link HazelcastLoader} object which internally handles the
 * configuration for the cache. Note that this cache does not provide access to all the local statistics parameters so some of them will return -1 as
 * result. There could happen that the number cache HITS is bigger than the number of total operations. This is caused by the fact that HITS number
 * indicates the number of hits on the local entries considering also the requests made by other cluster instances while the total operation count
 * indicates only the number of GET operations requested on the local cluster instance.
 * 
 * @author Nicola Lagomarsini Geosolutions
 */
public class HazelcastCacheProvider implements CacheProvider, DisposableBean {

    /** {@link Logger} object used for logging operations */
    private final static Log LOGGER = LogFactory.getLog(HazelcastCacheProvider.class);

    /** Fixed name for the Hazelcast map */
    public static final String HAZELCAST_MAP_DEFINITION = "CacheProviderMap";

    /** Converter from Mb to Bytes */
    public static final long MB_TO_BYTES = 1048576;

    /** Name of the {@link CacheProvider} used as Label */
    private static final String HAZELCAST_NAME = "Hazelcast Cache";

    /** Hazelcast {@link IMap} */
    private final IMap<String, TileObject> map;

    /** Boolean indicating that the Cache has been configured */
    private final boolean configured;

    /** Long value indicating the total size in Bytes */
    private final long totalSize;

    public HazelcastCacheProvider(HazelcastLoader loader) {
        configured = loader.isConfigured();
        // If the Hazelcast instance is configured, then the other
        // cacheProvider parameters are defined
        if (configured) {
            map = loader.getInstance().getMap(HAZELCAST_MAP_DEFINITION);
            totalSize = loader.getInstance().getConfig().getMapConfig(HAZELCAST_MAP_DEFINITION)
                    .getMaxSizeConfig().getSize()
                    * MB_TO_BYTES;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Configured Hazelcast Cache");
            }
        } else {
            map = null;
            totalSize = 0;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Hazelcast Cache not configured");
            }
        }
    }

    @Override
    public TileObject getTileObj(TileObject obj) {
        if (configured) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Getting TileObject:" + obj);
            }
            String key = GuavaCacheProvider.generateTileKey(obj);
            return map.get(key);
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cache not configured");
            }
            return null;
        }
    }

    @Override
    public void putTileObj(TileObject obj) {
        if (configured) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Adding TileObject:" + obj);
            }
            String key = GuavaCacheProvider.generateTileKey(obj);
            map.put(key, obj);
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cache not configured");
            }
        }
    }

    @Override
    public void removeTileObj(TileObject obj) {
        if (configured) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Removing TileObject:" + obj);
            }
            String key = GuavaCacheProvider.generateTileKey(obj);
            map.remove(key);
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cache not configured");
            }
        }
    }

    @Override
    public void removeLayer(String layername) {
        if (configured) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Removing Layer:" + layername);
            }
            // Creation of the Predicate
            EntryObject e = new PredicateBuilder().getEntryObject();
            Predicate predicate = e.get("layer_name").equal(layername);
            // Creation of the processor
            CacheEntryProcessor entryProcessor = new CacheEntryProcessor();
            // Execution of the Processor
            map.executeOnEntries(entryProcessor, predicate);
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cache not configured");
            }
        }
    }

    @Override
    public void clear() {
        if (configured) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Clearing cache");
            }
            map.clear();
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cache not configured");
            }
        }
    }

    @Override
    public void reset() {
        if (configured) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Resetting cache");
            }
            map.clear();
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cache not configured");
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        if (configured) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Destroying cache");
            }
            map.destroy();
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cache not configured");
            }
        }
    }

    @Override
    public CacheStatistics getStatistics() {
        if (configured) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Getting cache statistics");
            }
            // Getting statistics and then creating a new HazelcastCacheStatistics instance
            LocalMapStats localMapStats = map.getLocalMapStats();
            CacheStatistics stats = new HazelcastCacheStatistics(localMapStats, totalSize);
            return stats;
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cache not configured");
            }
        }
        return new CacheStatistics();
    }

    @Override
    public void configure(CacheConfiguration configuration) {
    }

    @Override
    public void addUncachedLayer(String layername) {
    }

    @Override
    public void removeUncachedLayer(String layername) {
    }

    @Override
    public boolean containsUncachedLayer(String layername) {
        return false;
    }

    @Override
    public List<EvictionPolicy> getSupportedPolicies() {
        return null;
    }

    @Override
    public boolean isImmutable() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return configured;
    }

    @Override
    public String getName() {
        return HAZELCAST_NAME;
    }

    /**
     * {@link CacheStatistics} extensions used for handling local map statistics
     * 
     * @author Nicola Lagomarsini Geosolutions
     */
    static class HazelcastCacheStatistics extends CacheStatistics {

        public HazelcastCacheStatistics(LocalMapStats localMapStats, long totalSize) {
            // Note that HITS indicates all the hits to the local entries, even if the request
            // is made from another cluster instance
            long hits = localMapStats.getHits();
            setHitCount(hits);
            // Total indicates the total number of the GET operations made by the local cache
            long total = localMapStats.getGetOperationCount();
            // Miss count not defined
            setMissCount(-1);
            setTotalCount(total);
            // If miss is not present, rate cannot be calculated
            double hitRate = -1;
            double missRate = -1;
            setHitRate(hitRate);
            setMissRate(missRate);
            // Setting total cache size
            setTotalSize(totalSize);
            // Setting actual size
            long actualSize = localMapStats.getOwnedEntryMemoryCost();
            setActualSize(actualSize);
            // Calculation of the memory occupation
            int currentMemoryOccupation = (int) (100L - (1L) * (100 * ((1.0d) * (totalSize - actualSize)) / totalSize));
            if (currentMemoryOccupation < 0) {
                currentMemoryOccupation = 0;
            }
            setCurrentMemoryOccupation(currentMemoryOccupation);
            // Eviction count not defined
            setEvictionCount(-1);
        }
    }

    /**
     * {@link EntryProcessor} implementation used for removing defined entries
     * 
     * @author Nicola Lagomarsini Geosolutions
     */
    static class CacheEntryProcessor implements EntryProcessor<String, TileObject> {

        @Override
        public Object process(Entry<String, TileObject> entry) {
            // By setting the entry value to null the entry is evicted
            entry.setValue(null);
            return null;

        }

        @Override
        public EntryBackupProcessor<String, TileObject> getBackupProcessor() {
            return null;
        }
    }
}
