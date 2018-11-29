package org.geowebcache.diskquota.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.apache.commons.dbcp.BasicDataSource;
import org.junit.Assume;

public class OracleQuotaStoreTest extends JDBCQuotaStoreTest {

    protected SQLDialect getDialect() {
        return new OracleDialect();
    }

    @Override
    protected JDBCFixtureRule makeFixtureRule() {
        return new JDBCFixtureRule(getFixtureId()) {
            @Override
            void checkAvailable() {
                try {
                    Class.forName("oracle.jdbc.driver.OracleDriver");
                } catch (Exception e) {
                    Assume.assumeFalse("Oracle driver not available", true);
                }
                super.checkAvailable();
            }

            @Override
            protected Properties createExampleFixture() {
                Properties p = new Properties();
                p.put("driver", "oracle.jdbc.driver.OracleDriver");
                p.put("url", "jdbc:oracle:thin:@localhost:1521:xe");
                p.put("username", "geoserver");
                p.put("password", "postgis");

                return p;
            }
        };
    }

    protected BasicDataSource getDataSource() throws IOException, SQLException {
        BasicDataSource dataSource = super.getDataSource();

        // cleanup
        try (Connection cx = dataSource.getConnection();
                Statement st = cx.createStatement(); ) {
            try {
                st.execute("DROP TABLE TILEPAGE CASCADE CONSTRAINTS");
            } catch (Exception e) {
                e.printStackTrace();
                // fine
            }
            try {
                st.execute("DROP TABLE TILESET CASCADE CONSTRAINTS");
            } catch (Exception e) {
                e.printStackTrace();
                // fine too
            }
        }

        return dataSource;
    }

    @Override
    protected String getFixtureId() {
        return "oracle";
    }
}
