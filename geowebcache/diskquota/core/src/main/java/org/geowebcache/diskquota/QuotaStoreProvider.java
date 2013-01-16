package org.geowebcache.diskquota;

import java.io.IOException;
import java.util.List;

import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.ConfigurationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class QuotaStoreProvider implements ApplicationContextAware, InitializingBean,
        DisposableBean {

    protected QuotaStore store;

    protected ApplicationContext applicationContext;

    protected ConfigLoader loader;

    public QuotaStoreProvider(ConfigLoader loader) {
        this.loader = loader;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public synchronized QuotaStore getQuotaStore() throws ConfigurationException, IOException {
        return store;
    }

    public void destroy() throws Exception {
        store.close();
    }

    public void afterPropertiesSet() throws Exception {
        reloadQuotaStore();
    }

    public void reloadQuotaStore() throws IOException, ConfigurationException {
        DiskQuotaConfig config = loader.loadConfig();
        String quotaStoreName = config.getQuotaStore();
        if(quotaStoreName == null) {
            // the default quota store, for backwards compatibility
            quotaStoreName = "BDB";
        }

        store = getQuotaStoreByName(quotaStoreName);
    }

    protected QuotaStore getQuotaStoreByName(String quotaStoreName) throws ConfigurationException, IOException  {
        List<QuotaStoreFactory> factories = GeoWebCacheExtensions.extensions(
                QuotaStoreFactory.class, applicationContext);
        for (QuotaStoreFactory factory : factories) {
            QuotaStore store = factory.getQuotaStore(applicationContext, quotaStoreName);
            if (store != null) {
                return store;
            }
        }

        throw new IllegalStateException("Could not find a quota store named " + quotaStoreName);
    }

}
