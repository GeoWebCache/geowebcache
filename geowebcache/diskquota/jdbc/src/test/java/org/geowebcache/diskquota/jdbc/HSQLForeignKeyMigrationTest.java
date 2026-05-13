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
package org.geowebcache.diskquota.jdbc;

import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.junit.After;
import org.junit.Before;

/**
 * Surefire-run unit test that runs {@link AbstractForeignKeyMigrationTest} against an in-memory HSQL database.
 *
 * <p>Each test instance gets a fresh, uniquely-named in-memory database (the {@link #INSTANCE_COUNTER} suffix isolates
 * parallel/repeated runs); the connection pool is closed in {@link #tearDown()} so no state leaks across tests.
 */
public class HSQLForeignKeyMigrationTest extends AbstractForeignKeyMigrationTest {

    private static int INSTANCE_COUNTER = 0;

    private BasicDataSource dataSource;

    @Before
    public void setUpDataSource() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
        dataSource.setUrl("jdbc:hsqldb:mem:legacy-fk-" + (++INSTANCE_COUNTER));
        dataSource.setUsername("sa");
        dataSource.setPassword("");
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
        return new HSQLDialect();
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
