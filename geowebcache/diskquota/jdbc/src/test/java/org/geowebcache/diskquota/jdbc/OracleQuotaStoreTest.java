package org.geowebcache.diskquota.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.dbcp.BasicDataSource;

public class OracleQuotaStoreTest extends JDBCQuotaStoreTest {



    protected SQLDialect getDialect() {
        return new OracleDialect();
    }

    protected BasicDataSource getDataSource() throws IOException, SQLException {
        BasicDataSource dataSource = new BasicDataSource();

        dataSource.setDriverClassName("oracle.jdbc.driver.OracleDriver");
        dataSource.setUrl("jdbc:oracle:thin:@localhost:1521:xe");
        dataSource.setUsername("geoserver");
        dataSource.setPassword("postgis");
        dataSource.setPoolPreparedStatements(true);
        dataSource.setAccessToUnderlyingConnectionAllowed(true);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(4);
        // if we cannot get a connection within 5 seconds give up
        dataSource.setMaxWait(5000);
       
        // cleanup
        Connection cx = null;
        Statement st = null;
        try {
            cx = dataSource.getConnection();
            st = cx.createStatement();
            try {
                st.execute("DROP TABLE TILEPAGE CASCADE CONSTRAINTS");
            } catch(Exception e) {
                e.printStackTrace();
                // fine
            }
            try {
                st.execute("DROP TABLE TILESET CASCADE CONSTRAINTS");
            } catch(Exception e) {
                e.printStackTrace();
                // fine too
            }
        } finally {
            st.close();
            cx.close();
        }
        
        return dataSource;
    }

}
