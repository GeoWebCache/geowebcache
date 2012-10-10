package org.geowebcache.diskquota.bdb;

import org.geowebcache.config.ConfigurationException;
import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.QuotaStoreFactory;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.context.ApplicationContext;

public class BDBQuotaStoreFactory implements QuotaStoreFactory {

    public static final String STORE_NAME = "bdb";

    public QuotaStore getQuotaStore(ApplicationContext ctx, String quotaStoreName) throws Exception {
        if (!STORE_NAME.equals(quotaStoreName)) {
            return null;
        }

        DefaultStorageFinder cacheDirFinder = (DefaultStorageFinder) ctx.getBean("gwcDefaultStorageFinder");
        TilePageCalculator tilePageCalculator = (TilePageCalculator) ctx
                .getBean("gwcTilePageCalculator");
        BDBQuotaStore bdbQuotaStore = new BDBQuotaStore(cacheDirFinder, tilePageCalculator);
        bdbQuotaStore.startUp();
        
        return bdbQuotaStore;
    }

}
