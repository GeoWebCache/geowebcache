/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
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

public class QuotaStoreProvider
        implements ApplicationContextAware, InitializingBean, DisposableBean {

    protected QuotaStore store;

    protected ApplicationContext applicationContext;

    protected ConfigLoader loader;

    public QuotaStoreProvider(ConfigLoader loader) {
        this.loader = loader;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public synchronized QuotaStore getQuotaStore() throws ConfigurationException, IOException {
        return store;
    }

    @Override
    public void destroy() throws Exception {
        store.close();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        replaceH2WithHsql();
        reloadQuotaStore();
    }

    private void replaceH2WithHsql() throws IOException, ConfigurationException {
        // migrate existing H2 DB selection to HSQL DB
        DiskQuotaConfig config = loader.loadConfig();
        if (config.getQuotaStore() != null && config.getQuotaStore().equals("H2")) {
            config.setQuotaStore("HSQL");
            loader.saveConfig(config);
        }
    }

    public void reloadQuotaStore() throws IOException, ConfigurationException {
        DiskQuotaConfig config = loader.loadConfig();
        String quotaStoreName = config.getQuotaStore();
        if (quotaStoreName == null) {
            // the default quota store, for backwards compatibility
            quotaStoreName = "BDB";
        }

        store = getQuotaStoreByName(quotaStoreName);
    }

    protected QuotaStore getQuotaStoreByName(String quotaStoreName)
            throws ConfigurationException, IOException {
        List<QuotaStoreFactory> factories =
                GeoWebCacheExtensions.extensions(QuotaStoreFactory.class, applicationContext);
        for (QuotaStoreFactory factory : factories) {
            QuotaStore store = factory.getQuotaStore(applicationContext, quotaStoreName);
            if (store != null) {
                return store;
            }
        }

        throw new IllegalStateException("Could not find a quota store named " + quotaStoreName);
    }
}
