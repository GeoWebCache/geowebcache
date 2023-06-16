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
 * @author Andrea Aime - GeoSolutions Copyright 2012
 */
package org.geowebcache.diskquota.jdbc;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.NamingException;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.geotools.util.factory.GeoTools;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.config.ConfigurationResourceProvider;
import org.geowebcache.config.XMLFileResourceProvider;
import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.QuotaStoreFactory;
import org.geowebcache.diskquota.jdbc.JDBCConfiguration.ConnectionPoolConfiguration;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Builds the quota store for the JDBC family (either H2, HSQL or JDBC)
 *
 * @author Andrea Aime - GeoSolutions
 */
public class JDBCQuotaStoreFactory implements QuotaStoreFactory, ApplicationContextAware {

    private static final Logger log = Logging.getLogger(JDBCQuotaStore.class.getName());

    private static final String CONFIGURATION_FILE_NAME = "geowebcache-diskquota-jdbc.xml";

    public static final String H2_STORE = "H2";

    public static final String HSQL_STORE = "HSQL";

    public static final String JDBC_STORE = "JDBC";

    // this will be set to true only for integration tests
    // not recommended for production usage
    public static boolean ENABLE_HSQL_AUTO_SHUTDOWN = false;

    private ApplicationContext appContext;

    private ConfigurationResourceProvider defaultResourceProvider;

    public JDBCQuotaStoreFactory() {}

    public JDBCQuotaStoreFactory(final ConfigurationResourceProvider resourceProvider) {
        this.defaultResourceProvider = resourceProvider;
    }

    @Override
    public List<String> getSupportedStoreNames() {
        List<String> supportedStores = new ArrayList<>();
        supportedStores.add(HSQL_STORE);
        supportedStores.add(JDBC_STORE);
        try {
            // check if H2 driver is in the classpath
            Class.forName("org.h2.Driver");
            supportedStores.add(H2_STORE);
        } catch (Exception e) {
            // don't add h2 when its driver is not there
        }
        return supportedStores;
    }

    @Override
    public QuotaStore getQuotaStore(ApplicationContext ctx, String quotaStoreName)
            throws ConfigurationException {
        // lookup dependencies in the classpath
        DefaultStorageFinder cacheDirFinder =
                (DefaultStorageFinder) ctx.getBean("gwcDefaultStorageFinder");
        TilePageCalculator tilePageCalculator =
                (TilePageCalculator) ctx.getBean("gwcTilePageCalculator");

        if (H2_STORE.equals(quotaStoreName)) {
            return initializeH2Store(cacheDirFinder, tilePageCalculator);
        } else if (HSQL_STORE.equals(quotaStoreName)) {
            return initializeHSQLStore(cacheDirFinder, tilePageCalculator);
        } else if (JDBC_STORE.equals(quotaStoreName)) {
            return getJDBCStore(cacheDirFinder, tilePageCalculator);
        }

        return null;
    }

    private ConfigurationResourceProvider createResourceProvider(
            DefaultStorageFinder cacheDirFinder) throws ConfigurationException {
        return new XMLFileResourceProvider(
                CONFIGURATION_FILE_NAME,
                (org.springframework.web.context.WebApplicationContext) appContext,
                cacheDirFinder);
    }

    private QuotaStore getJDBCStore(
            DefaultStorageFinder cacheDirFinder, TilePageCalculator tilePageCalculator)
            throws ConfigurationException {
        JDBCConfiguration config = null;

        ConfigurationResourceProvider resourceProvider =
                defaultResourceProvider == null
                        ? createResourceProvider(cacheDirFinder)
                        : defaultResourceProvider;

        try {
            if (!resourceProvider.hasInput()) {
                throw new IllegalArgumentException(
                        "Unable to read JDBC configuration file: "
                                + resourceProvider.getLocation());
            }
            config = JDBCConfiguration.load(resourceProvider.in());
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to read JDBC configuration file at " + resourceProvider.getId());
        }

        return getJDBCStore(cacheDirFinder, tilePageCalculator, config);
    }

    public QuotaStore getJDBCStore(ApplicationContext ctx, JDBCConfiguration config)
            throws ConfigurationException {
        // lookup dependencies in the classpath
        DefaultStorageFinder cacheDirFinder =
                (DefaultStorageFinder) ctx.getBean("gwcDefaultStorageFinder");
        TilePageCalculator tilePageCalculator =
                (TilePageCalculator) ctx.getBean("gwcTilePageCalculator");

        return getJDBCStore(cacheDirFinder, tilePageCalculator, config);
    }

    private QuotaStore getJDBCStore(
            DefaultStorageFinder cacheDirFinder,
            TilePageCalculator tilePageCalculator,
            JDBCConfiguration config)
            throws ConfigurationException {
        JDBCConfiguration expandedConfig = config.clone(true);
        DataSource ds = getDataSource(expandedConfig);

        // prepare the dialect
        String dialectName = expandedConfig.getDialect();
        String dialectBeanName = dialectName + "QuotaDialect";
        Object bean = appContext.getBean(dialectBeanName);

        if (bean == null) {
            throw new ConfigurationException(
                    "Could not locate bean "
                            + dialectBeanName
                            + " for dialect "
                            + dialectName
                            + " in the Spring application context");
        } else if (!(bean instanceof SQLDialect)) {
            throw new ConfigurationException(
                    "Bean "
                            + dialectBeanName
                            + " for dialect "
                            + dialectName
                            + " was found in the Spring application "
                            + "context, but it's not a SQLDialect object, instead it's a "
                            + bean.getClass().getName());
        }

        SQLDialect dialect = (SQLDialect) bean;

        // build up the store
        JDBCQuotaStore store = new JDBCQuotaStore(cacheDirFinder, tilePageCalculator);
        store.setDataSource(ds);
        store.setDialect(dialect);
        // sets schema if configured in geowebcache-diskquota-jdbc.xml
        store.setSchema(expandedConfig.getSchema());

        // initialize it
        store.initialize();

        return store;
    }

    protected DataSource getDataSource(JDBCConfiguration config) throws ConfigurationException {
        try {
            DataSource ds = null;
            if (config.getJNDISource() != null) {
                ds = (DataSource) GeoTools.jndiLookup(config.getJNDISource());
                if (ds == null)
                    throw new ConfigurationException(
                            "Failed to get a datasource from: " + config.getJNDISource());
            } else if (config.getConnectionPool() != null) {
                ConnectionPoolConfiguration cp = config.getConnectionPool();

                BasicDataSource bds = new BasicDataSource();
                bds.setDriverClassName(cp.getDriver());
                bds.setUrl(cp.getUrl());
                bds.setUsername(cp.getUsername());
                bds.setPassword(cp.getPassword());
                bds.setPoolPreparedStatements(true);
                bds.setMaxOpenPreparedStatements(cp.getMaxOpenPreparedStatements());
                bds.setMinIdle(cp.getMinConnections());
                bds.setMaxActive(cp.getMaxConnections());
                bds.setMaxWait(cp.getConnectionTimeout() * 1000);
                bds.setValidationQuery(cp.getValidationQuery());

                ds = bds;
            } else {
                throw new IllegalArgumentException(
                        "JDBC configuration misses both JNDI source and connection pool");
            }

            // verify the datasource works
            Connection c = null;
            try { // NOPMD try-with-resources would just lead to an unused variable violation
                c = ds.getConnection();
            } catch (SQLException e) {
                throw new ConfigurationException(
                        "Failed to get a database connection: " + e.getMessage(), e);
            } finally {
                if (c != null) {
                    try {
                        c.close();
                    } catch (SQLException e) {
                        // nothing we can do about it, but at least let the admin know
                        log.log(
                                Level.FINE,
                                "An error occurred while closing the test JDBC connection: "
                                        + e.getMessage(),
                                e);
                    }
                }
            }

            return ds;
        } catch (NamingException e) {
            throw new ConfigurationException("Failed to locate the data source in JNDI", e);
        }
    }

    private QuotaStore initializeH2Store(
            DefaultStorageFinder cacheDirFinder, TilePageCalculator tilePageCalculator)
            throws ConfigurationException {
        // get a default data source located in the cache directory
        DataSource ds = getH2DataSource(cacheDirFinder);

        // build up the store
        JDBCQuotaStore store = new JDBCQuotaStore(cacheDirFinder, tilePageCalculator);
        store.setDataSource(ds);
        store.setDialect(new H2Dialect());

        // initialize it
        store.initialize();

        return store;
    }

    private QuotaStore initializeHSQLStore(
            DefaultStorageFinder cacheDirFinder, TilePageCalculator tilePageCalculator)
            throws ConfigurationException {
        // get a default data source located in the cache directory
        DataSource ds = getHSQLDataSource(cacheDirFinder);

        // build up the store
        JDBCQuotaStore store = new JDBCQuotaStore(cacheDirFinder, tilePageCalculator);
        store.setDataSource(ds);
        store.setDialect(new HSQLDialect());

        // initialize it
        store.initialize();

        return store;
    }

    /** Prepares a simple data source for the embedded H2 */
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

    /** Prepares a simple data source for the embedded HSQL */
    private DataSource getHSQLDataSource(DefaultStorageFinder cacheDirFinder)
            throws ConfigurationException {
        File storeDirectory =
                new File(cacheDirFinder.getDefaultPath(), "diskquota_page_store_hsql");
        storeDirectory.mkdirs();

        BasicDataSource dataSource = new BasicDataSource();

        dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
        String database = new File(storeDirectory, "diskquota").getAbsolutePath();
        dataSource.setUrl(
                "jdbc:hsqldb:file:"
                        + database
                        + (ENABLE_HSQL_AUTO_SHUTDOWN ? ";shutdown=true" : ""));
        dataSource.setUsername("sa");
        dataSource.setPoolPreparedStatements(true);
        dataSource.setAccessToUnderlyingConnectionAllowed(true);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(-1); // boundless
        dataSource.setMaxWait(5000);
        return dataSource;
    }

    @Override
    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.appContext = appContext;
    }
}
