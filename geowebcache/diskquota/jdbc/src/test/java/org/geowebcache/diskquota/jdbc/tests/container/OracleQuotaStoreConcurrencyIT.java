/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2026
 */
package org.geowebcache.diskquota.jdbc.tests.container;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.geowebcache.diskquota.jdbc.AbstractJDBCQuotaStoreConcurrencyTest;
import org.geowebcache.diskquota.jdbc.OracleDialect;
import org.geowebcache.diskquota.jdbc.SQLDialect;
import org.geowebcache.testcontainers.jdbc.OracleXEContainer;
import org.junit.ClassRule;

/**
 * Runs {@link AbstractJDBCQuotaStoreConcurrencyTest} against Oracle XE via Testcontainers. Oracle throws ORA-08176
 * (consistent-read failure across recent DDL) and ORA-08177 (serialization failure); both go through the retry layer.
 */
public class OracleQuotaStoreConcurrencyIT extends AbstractJDBCQuotaStoreConcurrencyTest {

    @ClassRule
    public static final OracleXEContainer ORACLE = OracleXEContainer.latest().disabledWithoutDocker();

    @Override
    protected DataSource newDataSource() {
        return newPooledDataSource(
                ORACLE.getDriverClassName(), ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword());
    }

    @Override
    protected SQLDialect newDialect() {
        return new OracleDialect();
    }

    /** Oracle requires {@code CASCADE CONSTRAINTS} (not just {@code CASCADE}) to drop tables with FK dependents. */
    @Override
    protected void cleanupDatabase(DataSource ds) throws SQLException {
        try (Connection cx = ds.getConnection();
                Statement st = cx.createStatement()) {
            try {
                st.execute("DROP TABLE TILEPAGE CASCADE CONSTRAINTS");
            } catch (SQLException ignored) {
                // table may not exist on first run
            }
            try {
                st.execute("DROP TABLE TILESET CASCADE CONSTRAINTS");
            } catch (SQLException ignored) {
                // table may not exist on first run
            }
        }
    }
}
