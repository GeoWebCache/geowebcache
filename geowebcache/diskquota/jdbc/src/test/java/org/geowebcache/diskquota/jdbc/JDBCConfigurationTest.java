package org.geowebcache.diskquota.jdbc;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.diskquota.jdbc.JDBCConfiguration.ConnectionPoolConfiguration;
import org.springframework.context.ApplicationContext;

import junit.framework.TestCase;

public class JDBCConfigurationTest extends TestCase {

    ApplicationContext appContext = createMock(ApplicationContext.class);
    
    protected void setUp() throws Exception {
        super.setUp();
        System.setProperty("TEST_JDBC_DRIVER", "org.postgresql.Driver");
        System.setProperty("TEST_JDBC_URL", "jdbc:postgresql:gttest");
        System.setProperty("TEST_JDBC_USER", "test");
        System.setProperty("TEST_JDBC_PASSWORD", "toast");
        System.setProperty("ALLOW_ENV_PARAMETRIZATION", "true");

        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();
        GeoWebCacheEnvironment genv = new GeoWebCacheEnvironment();
        gse.setApplicationContext(appContext);

        expect(appContext.getBeanNamesForType(GeoWebCacheEnvironment.class)).andReturn(
                new String[] { "environment" });
        expect(appContext.getBean("environment")).andReturn(genv);
        Map<String, GeoWebCacheEnvironment> genvMap = new HashMap<>();
        genvMap.put("environment", genv);
        expect(appContext.getBeansOfType(GeoWebCacheEnvironment.class))
                .andReturn(genvMap).anyTimes();
        expect(appContext.getBean("environment")).andReturn(genv).anyTimes();
        
        replay(appContext);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        System.setProperty("TEST_JDBC_DRIVER", "");
        System.setProperty("TEST_JDBC_URL", "");
        System.setProperty("TEST_JDBC_USER", "");
        System.setProperty("TEST_JDBC_PASSWORD", "");
        System.setProperty("ALLOW_ENV_PARAMETRIZATION", "");
    }
    
    public void testRoundTripJNDI() throws Exception {
        JDBCConfiguration config = new JDBCConfiguration();
        config.setDialect("Oracle");
        config.setJNDISource("java:comp/env/jdbc/oralocal");
        File file = new File("./target/jndi-jdbc.xml");
        if(file.exists()) {
            assertTrue(file.delete());
        }
        
        // do a round trip and check it's the same
        config.store(config, file);
        JDBCConfiguration config2 = config.load(file);
        assertEquals(config2, config);
    }
    
    public void testRoundTripConnectionPool() throws Exception {
        JDBCConfiguration config = new JDBCConfiguration();
        config.setDialect("PostgreSQL");
        ConnectionPoolConfiguration cp = new ConnectionPoolConfiguration();
        cp.setDriver("org.postgresql.Driver");
        cp.setUrl("jdbc:postgresql:gttest");
        cp.setUsername("test");
        cp.setPassword("toast");
        cp.setMinConnections(1);
        cp.setMaxConnections(10);
        cp.setValidationQuery("select 1");
        cp.setMaxOpenPreparedStatements(50);
        config.setConnectionPool(cp);
        
        File file = new File("./target/dbcp-jdbc.xml");
        if(file.exists()) {
            assertTrue(file.delete());
        }
        
        // do a round trip and check it's the same
        config.store(config, file);
        JDBCConfiguration config2 = config.load(file);
        assertEquals(config2, config);
    }
    
    public void testRoundTripParametrizedConnectionPool() throws Exception {        
        JDBCConfiguration config = new JDBCConfiguration();
        config.setDialect("PostgreSQL");
        ConnectionPoolConfiguration cp = new ConnectionPoolConfiguration();
        cp.setDriver("${TEST_JDBC_DRIVER}");
        cp.setUrl("${TEST_JDBC_URL}");
        cp.setUsername("${TEST_JDBC_USER}");
        cp.setPassword("${TEST_JDBC_PASSWORD}");
        cp.setMinConnections(1);
        cp.setMaxConnections(10);
        cp.setValidationQuery("select 1");
        cp.setMaxOpenPreparedStatements(50);
        config.setConnectionPool(cp);
        
        File file1 = new File("./target/dbcp-jdbc1.xml");
        if(file1.exists()) {
            assertTrue(file1.delete());
        }

        File file2 = new File("./target/dbcp-jdbc2.xml");
        if(file2.exists()) {
            assertTrue(file2.delete());
        }

        // do a round trip and check values
        config.store(config.clone(true), file1);
        JDBCConfiguration config1 = config.load(file1);
        assertNotSame(config1, config);

        config.store(config.clone(false), file2);
        JDBCConfiguration config2 = config.load(file2);
        assertEquals(config2, config);
    }
}

