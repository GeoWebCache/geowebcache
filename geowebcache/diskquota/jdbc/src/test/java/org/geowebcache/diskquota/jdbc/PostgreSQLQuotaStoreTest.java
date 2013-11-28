package org.geowebcache.diskquota.jdbc;

import java.util.Properties;

public class PostgreSQLQuotaStoreTest extends JDBCQuotaStoreTest {

    protected SQLDialect getDialect() {
        return new PostgreSQLDialect();
    }
    
    @Override
    protected String getFixtureId() {
        return "postgresql";
    }
    
    @Override
    protected Properties createExampleFixture() {
        Properties p = new Properties();
        p.put("driver", "org.postgresql.Driver");
        p.put("url", "jdbc:postgresql:gttest");
        p.put("username", "cite");
        p.put("password", "cite");
        
        return p;
    }

}
