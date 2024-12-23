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
 * @author Gabriel Roldan Copyright 2011
 */
package org.geowebcache.diskquota.bdb;

import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.StoreConfig;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.diskquota.storage.PageStoreConfig;

public class EntityStoreBuilder {

    private static final Logger log = Logging.getLogger(EntityStoreBuilder.class.getName());

    private PageStoreConfig config;

    public EntityStoreBuilder(PageStoreConfig config) {
        this.config = config;
    }

    /**
     * @param bdbEnvProperties properties for the {@link EnvironmentConfig}, or {@code null}. If not provided
     *     {@code environment.properties} will be looked up for inside {@code storeDirectory}
     */
    public EntityStore buildEntityStore(final File storeDirectory, final Properties bdbEnvProperties) {

        EnvironmentConfig envCfg = new EnvironmentConfig();
        envCfg.setAllowCreate(true);
        envCfg.setCacheMode(CacheMode.DEFAULT);
        envCfg.setLockTimeout(1000, TimeUnit.MILLISECONDS);
        envCfg.setDurability(Durability.COMMIT_WRITE_NO_SYNC);
        envCfg.setSharedCache(true);
        envCfg.setTransactional(true);
        envCfg.setConfigParam("je.log.fileMax", String.valueOf(100 * 1024 * 1024));

        Integer cacheMemoryPercentAllowed = config.getCacheMemoryPercentAllowed();
        Integer cacheSizeMB = config.getCacheSizeMB();
        if (cacheMemoryPercentAllowed == null) {
            if (cacheSizeMB == null) {
                log.config("Neither disk quota page store' cache memory percent nor cache size was provided."
                        + " Defaulting to 25% Heap Size");
                envCfg.setCachePercent(25);
            } else {
                log.config("Disk quota page store cache explicitly set to " + cacheSizeMB + "MB");
                envCfg.setCacheSize(cacheSizeMB);
            }
        } else {
            envCfg.setCachePercent(cacheMemoryPercentAllowed);
        }

        Environment env = new Environment(storeDirectory, envCfg);
        String storeName = "GWC DiskQuota page store";
        StoreConfig config = new StoreConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        config.setModel(new DiskQuotaEntityModel());
        // config.setDeferredWrite(true);
        EntityStore entityStore = new EntityStore(env, storeName, config);
        return entityStore;
    }
}
