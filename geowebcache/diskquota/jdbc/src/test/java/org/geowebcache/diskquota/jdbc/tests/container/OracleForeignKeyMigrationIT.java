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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.geowebcache.diskquota.jdbc.AbstractForeignKeyMigrationTest;
import org.geowebcache.diskquota.jdbc.OracleDialect;
import org.geowebcache.diskquota.jdbc.SQLDialect;
import org.geowebcache.testcontainers.jdbc.OracleXEContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

/**
 * Runs {@link AbstractForeignKeyMigrationTest} against Oracle XE via Testcontainers.
 *
 * <p>The migrated FK is {@code DEFERRABLE INITIALLY DEFERRED} (Oracle has no {@code ON UPDATE CASCADE})
 */
public class OracleForeignKeyMigrationIT extends AbstractForeignKeyMigrationTest {

    @ClassRule
    public static final OracleXEContainer ORACLE = OracleXEContainer.latest().disabledWithoutDocker();

    private BasicDataSource dataSource;

    @Before
    public void setUpDataSource() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName(ORACLE.getDriverClassName());
        dataSource.setUrl(ORACLE.getJdbcUrl());
        dataSource.setUsername(ORACLE.getUsername());
        dataSource.setPassword(ORACLE.getPassword());
        recreateLegacySchema();
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    @Override
    protected SQLDialect dialect() {
        return new OracleDialect();
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    protected String legacyDdl(String ddl) {
        return ddl.replace("${schema}", "").replaceAll("\\s*DEFERRABLE INITIALLY DEFERRED", "");
    }

    @Override
    protected short expectedMigratedFkState() {
        return (short) DatabaseMetaData.importedKeyInitiallyDeferred;
    }

    @Override
    protected short readFkState(ResultSet rs) throws SQLException {
        return rs.getShort("DEFERRABILITY");
    }

    /** Oracle requires {@code CASCADE CONSTRAINTS} (not just {@code CASCADE}) to drop tables with FK dependents. */
    @Override
    protected String dropTableSql(String table) {
        return "DROP TABLE " + table + " CASCADE CONSTRAINTS";
    }
}
