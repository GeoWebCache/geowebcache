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
 * 
 * @author Andrea Aime - GeoSolutions
 */
package org.geowebcache.diskquota.jdbc;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.QuotaStoreFactory;
import org.geowebcache.diskquota.jdbc.JDBCConfiguration.ConnectionPoolConfiguration;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Builds the quota store for the JDBC family (either H2 or JDBC)
 * 
 * @author Andrea Aime - GeoSolutions
 *
 */
public class JDBCQuotaStoreFactory implements QuotaStoreFactory, ApplicationContextAware {
    
    private static final Log log = LogFactory.getLog(JDBCQuotaStore.class);

    public static final String H2_STORE = "H2";

    public static final String JDBC_STORE = "JDBC";
    
    private ApplicationContext appContext;
    
    public List<String> getSupportedStoreNames() {
        return Arrays.asList(H2_STORE, JDBC_STORE);
    }

    public QuotaStore getQuotaStore(ApplicationContext ctx, String quotaStoreName)
            throws ConfigurationException {
        // lookup dependencies in the classpath
        DefaultStorageFinder cacheDirFinder = (DefaultStorageFinder) ctx
                .getBean("gwcDefaultStorageFinder");
        TilePageCalculator tilePageCalculator = (TilePageCalculator) ctx
                .getBean("gwcTilePageCalculator");

        DataSource ds = null;
        if (H2_STORE.equals(quotaStoreName)) {
            return initializeH2Store(cacheDirFinder, tilePageCalculator);
        } else if (JDBC_STORE.equals(quotaStoreName)) {
            return getJDBCStore(cacheDirFinder, tilePageCalculator);
        }

        return null;
    }

    private QuotaStore getJDBCStore(DefaultStorageFinder cacheDirFinder,
            TilePageCalculator tilePageCalculator) throws ConfigurationException {
        JDBCConfiguration config = null;
        
        File configFile = new File(cacheDirFinder.getDefaultPath(),
                "geowebcache-diskquota-jdbc.xml");
        if (!configFile.exists()) {
            throw new IllegalArgumentException("Failed to locate JDBC configuration file: "
                    + configFile.getAbsolutePath());
        }
        config = JDBCConfiguration.load(configFile);
        return getJDBCStore(cacheDirFinder, tilePageCalculator, config);
    }
    
    public QuotaStore getJDBCStore(ApplicationContext ctx, JDBCConfiguration config) throws ConfigurationException {
        // lookup dependencies in the classpath
        DefaultStorageFinder cacheDirFinder = (DefaultStorageFinder) ctx
                .getBean("gwcDefaultStorageFinder");
        TilePageCalculator tilePageCalculator = (TilePageCalculator) ctx
                .getBean("gwcTilePageCalculator");

        return getJDBCStore(cacheDirFinder, tilePageCalculator, config);
    }

    private QuotaStore getJDBCStore(DefaultStorageFinder cacheDirFinder,
            TilePageCalculator tilePageCalculator, JDBCConfiguration config)
            throws ConfigurationException {
        DataSource ds = getDataSource(config);
        
        // prepare the dialect
        String dialectName = config.getDialect();
        String dialectBeanName = dialectName + "QuotaDialect";
        Object bean = appContext.getBean(dialectBeanName);
        
        if(bean == null) {
            throw new ConfigurationException("Could not locate bean " + dialectBeanName
                    + " for dialect " + dialectName + " in the Spring application context");
        } else if(!(bean instanceof SQLDialect)) {
            throw new ConfigurationException("Bean " + dialectBeanName
                    + " for dialect " + dialectName + " was found in the Spring application " 
                    + "context, but it's not a SQLDialect object, instead it's a " + bean.getClass().getName());
        }
        
        SQLDialect dialect = (SQLDialect) bean;

        // build up the store
        JDBCQuotaStore store = new JDBCQuotaStore(cacheDirFinder, tilePageCalculator);
        store.setDataSource(ds);
        store.setDialect(dialect);

        // initialize it
        store.initialize();

        return store;
    }

    private DataSource getDataSource(JDBCConfiguration config) throws ConfigurationException {
        try {
            DataSource ds = null;
            if (config.getJNDISource() != null) {
                InitialContext context = new InitialContext();
                ds = (DataSource) context.lookup(config.getJNDISource());
            } else if(config.getConnectionPool() != null){
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
            }
            
            // verify the datasource works
            Connection c = null;
            try {
                c = ds.getConnection();
            } catch(SQLException e) {
                throw new ConfigurationException("Failed to get a database connection: " + e.getMessage(), e);
            } finally {
                if(c != null) {
                    try {
                        c.close();
                    } catch(SQLException e) {
                        // nothing we can do about it, but at least let the admin know
                        log.debug("An error occurred while closing the test JDBC connection: " + e.getMessage(), e);
                    }
                }
            }
            
            return ds;
        } catch (NamingException e) {
            throw new ConfigurationException("Failed to locate the data source in JNDI", e);
        }
    }

    private QuotaStore initializeH2Store(DefaultStorageFinder cacheDirFinder,
            TilePageCalculator tilePageCalculator) throws ConfigurationException {
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

    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.appContext = appContext;
    }
}
