package org.geowebcache.diskquota.jdbc;

import java.util.Properties;
import org.junit.Test;

public class H2QuotaStoreTest extends JDBCQuotaStoreTest {

    @Override
    protected SQLDialect getDialect() {
        return new H2Dialect();
    }

    @Override
    protected JDBCFixtureRule makeFixtureRule() {
        return new JDBCFixtureRule(getFixtureId()) {
            @Override
            protected Properties createOfflineFixture() {
                Properties fixture = new Properties();
                fixture.put("driver", "org.h2.Driver");
                fixture.put("url", "jdbc:h2:./target/quota-h2");
                fixture.put("username", "sa");
                fixture.put("password", "");
                return fixture;
            }
        };
    }

    @Override
    protected String getFixtureId() {
        return "h2";
    }

    @Test
    public void checkConnectionTest() {}
}
