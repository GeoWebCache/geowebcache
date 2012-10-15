package org.geowebcache.diskquota.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.commons.dbcp.BasicDataSource;

public class OracleQuotaStoreTest extends JDBCQuotaStoreTest {

    protected SQLDialect getDialect() {
        return new OracleDialect();
    }

    @Override
    boolean checkAvailable() {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (Exception e) {
            System.out.println("Skipping " + OracleQuotaStoreTest.class
                    + " tests, Oracle driver not available");
            return false;
        }
        return super.checkAvailable();
    }

    protected BasicDataSource getDataSource() throws IOException, SQLException {
        BasicDataSource dataSource = super.getDataSource();

        // cleanup
        Connection cx = null;
        Statement st = null;
        try {
            cx = dataSource.getConnection();
            st = cx.createStatement();
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
        } finally {
            st.close();
            cx.close();
        }

        return dataSource;
    }

    @Override
    protected String getFixtureId() {
        return "oracle";
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

}
