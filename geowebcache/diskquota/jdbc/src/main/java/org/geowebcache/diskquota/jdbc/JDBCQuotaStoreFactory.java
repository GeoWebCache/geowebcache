package org.geowebcache.diskquota.jdbc;

import java.io.File;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.QuotaStoreFactory;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.context.ApplicationContext;

public class JDBCQuotaStoreFactory implements QuotaStoreFactory {

    public static final String H2_STORE = "h2";

    public static final String JDBC_STORE = "jdbc";

    public QuotaStore getQuotaStore(ApplicationContext ctx, String quotaStoreName)
            throws ConfigurationException {
        // lookup dependencies in the classpath 
        DefaultStorageFinder cacheDirFinder = (DefaultStorageFinder) ctx
                .getBean("gwcDefaultStorageFinder");
        TilePageCalculator tilePageCalculator = (TilePageCalculator) ctx
                .getBean("gwcTilePageCalculator");

        
        if (H2_STORE.equals(quotaStoreName)) {
            // get a default data source located in the cache directory
            DataSource ds = getH2DataSource(cacheDirFinder);
            
            // build up the store
            JDBCQuotaStore store = new JDBCQuotaStore(cacheDirFinder, tilePageCalculator);
            store.setDataSource(ds);
            store.setDialect(new H2Dialect());

            // initialize it
            store.initialize();
            
            return store;
        } else if (JDBC_STORE.equals(quotaStoreName)) {
            throw new UnsupportedOperationException("Generic 'jdbc' store still not wired up");
        }

        return null;
    }

    /**
     * Prepares a simple data source for the embedded H2
     * 
     * @param cacheDirFinder
     * @return
     * @throws ConfigurationException
     */
    private DataSource getH2DataSource(DefaultStorageFinder cacheDirFinder)
            throws ConfigurationException {
        File storeDirectory = new File(cacheDirFinder.getDefaultPath(), "diskquota_page_store_h2");
        storeDirectory.mkdirs();

        BasicDataSource dataSource = new BasicDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        String database = new File(storeDirectory, "diskquota").getAbsolutePath();
        dataSource.setUrl("jdbc:h2:" + database);
        dataSource.setUsername("sa");
        dataSource.setPoolPreparedStatements(true);
        dataSource.setAccessToUnderlyingConnectionAllowed(true);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(-1); // boundless
        dataSource.setMaxWait(5000);
        return dataSource;
    }
}
