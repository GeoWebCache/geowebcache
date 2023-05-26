package org.geowebcache.diskquota.jdbc;

import java.util.Properties;

public class HSQLQuotaStoreTest extends JDBCQuotaStoreTest {

    @Override
    protected SQLDialect getDialect() {
        return new HSQLDialect();
    }

    @Override
    protected JDBCFixtureRule makeFixtureRule() {
        return new JDBCFixtureRule(getFixtureId()) {

            @Override
            protected Properties createOfflineFixture() {
                Properties fixture = new Properties();
                fixture.put("driver", "org.hsqldb.jdbcDriver");
                fixture.put("url", "jdbc:hsqldb:file:./target/quota-hsql");
                fixture.put("username", "sa");
                fixture.put("password", "");
                return fixture;
            }
        };
    }

    @Override
    protected String getFixtureId() {
        return "hsql";
    }
}
