package org.geowebcache.diskquota.jdbc;

import java.util.Properties;

public class PostgreSQLQuotaStoreTest extends JDBCQuotaStoreTest {

    @Override
    protected SQLDialect getDialect() {
        return new PostgreSQLDialect();
    }

    @Override
    protected String getFixtureId() {
        return "postgresql";
    }

    @Override
    protected JDBCFixtureRule makeFixtureRule() {
        return new JDBCFixtureRule(getFixtureId()) {
            @Override
            protected Properties createExampleFixture() {
                Properties p = new Properties();
                p.put("driver", "org.postgresql.Driver");
                p.put("url", "jdbc:postgresql:gttest");
                p.put("username", "cite");
                p.put("password", "cite");

                return p;
            }
        };
    }
}
