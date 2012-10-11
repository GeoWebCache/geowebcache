package org.geowebcache.diskquota.jdbc;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.commons.dbcp.BasicDataSource;

public class H2QuotaStoreTest extends JDBCQuotaStoreTest {

    protected SQLDialect getDialect() {
        return new H2Dialect();
    }

    protected BasicDataSource getDataSource() throws IOException {
        // cleanup previous eventual db
        File[] files = new File("./target").listFiles(new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return name.startsWith("quota-h2");
            }
        });
        for (File file : files) {
            assertTrue(file.delete());
        }

        BasicDataSource dataSource = new BasicDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:./target/quota-h2");
        dataSource.setUsername("sa");
        dataSource.setPoolPreparedStatements(true);
        dataSource.setAccessToUnderlyingConnectionAllowed(true);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(4);
        // if we cannot get a connection within 5 seconds give up
        dataSource.setMaxWait(5000);
        return dataSource;
    }

}
