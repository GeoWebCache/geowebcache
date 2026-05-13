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

import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.geowebcache.diskquota.jdbc.AbstractForeignKeyMigrationTest;
import org.geowebcache.diskquota.jdbc.PostgreSQLDialect;
import org.geowebcache.diskquota.jdbc.SQLDialect;
import org.geowebcache.testcontainers.jdbc.PostgresContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

/**
 * Integration test that runs {@link AbstractForeignKeyMigrationTest} against a real PostgreSQL spun up via
 * Testcontainers. Validates that the FK-upgrade path works against the JDBC driver's
 * {@code DatabaseMetaData.getImportedKeys} semantics on PostgreSQL, not just HSQL.
 */
public class PostgreSQLForeignKeyMigrationIT extends AbstractForeignKeyMigrationTest {

    @ClassRule
    public static final PostgresContainer POSTGRES = PostgresContainer.latest().disabledWithoutDocker();

    private BasicDataSource dataSource;

    @Before
    public void setUpDataSource() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
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
        return new PostgreSQLDialect();
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
