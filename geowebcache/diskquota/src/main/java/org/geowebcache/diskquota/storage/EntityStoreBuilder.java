package org.geowebcache.diskquota.storage;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.StoreConfig;

public class EntityStoreBuilder {

    private static final Log log = LogFactory.getLog(EntityStoreBuilder.class);

    private PageStoreConfig config;

    public EntityStoreBuilder(PageStoreConfig config) {
        this.config = config;
    }

    /**
     * 
     * @param storeDirectory
     * @param bdbEnvProperties
     *            properties for the {@link EnvironmentConfig}, or {@code null}. If not provided
     *            {@code environment.properties} will be looked up for inside {@code storeDirectory}
     * @return
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
                log.info("Neither disk quota page store' cache memory percent nor cache size was provided."
                        + " Defaulting to 25% Heap Size");
                envCfg.setCachePercent(25);
            } else {
                log.info("Disk quota page store cache explicitly set to " + cacheSizeMB + "MB");
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
        // config.setDeferredWrite(true);
        EntityStore entityStore = new EntityStore(env, storeName, config);
        return entityStore;
    }
}
