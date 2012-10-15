package org.geowebcache.diskquota.jdbc;

import java.util.Properties;

public class H2QuotaStoreTest extends JDBCQuotaStoreTest {

    protected SQLDialect getDialect() {
        return new H2Dialect();
    }
    
    @Override
    protected Properties createOfflineFixture() {
        Properties fixture = new Properties();
        fixture.put( "driver","org.h2.Driver");
        fixture.put( "url","jdbc:h2:./target/quota-h2");
        fixture.put( "username","sa");
        fixture.put( "password","");
        return fixture;
    }

    @Override
    protected String getFixtureId() {
        return "h2";
    }

}
