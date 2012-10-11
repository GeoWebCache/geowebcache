package org.geowebcache.diskquota.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.dbcp.BasicDataSource;

public class PostgreSQLQuotaStoreTest extends JDBCQuotaStoreTest {

    protected SQLDialect getDialect() {
        return new H2Dialect();
    }

    protected BasicDataSource getDataSource() throws IOException, SQLException {
        BasicDataSource dataSource = new BasicDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:postgresql:gttest");
        dataSource.setUsername("cite");
        dataSource.setPassword("cite");
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
                st.execute("DROP TABLE TILEPAGE CASCADE");
            } catch(Exception e) {
                // fine
            }
            try {
                st.execute("DROP TABLE TILESET CASCADE");
            } catch(Exception e) {
                // fine too
            }
        } finally {
            st.close();
            cx.close();
        }
        
        return dataSource;
    }

}
