package org.geowebcache.diskquota.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;

public class PostgreSQLQuotaStoreTest extends JDBCQuotaStoreTest {

    protected SQLDialect getDialect() {
        return new H2Dialect();
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
