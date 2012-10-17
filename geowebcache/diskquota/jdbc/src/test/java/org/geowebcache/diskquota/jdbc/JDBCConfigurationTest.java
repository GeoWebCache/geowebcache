package org.geowebcache.diskquota.jdbc;

import java.io.File;

import org.geowebcache.diskquota.jdbc.JDBCConfiguration.ConnectionPoolConfiguration;

import junit.framework.TestCase;

public class JDBCConfigurationTest extends TestCase {

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
}

