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
import org.geowebcache.diskquota.jdbc.AbstractJDBCQuotaStoreConcurrencyTest;
import org.geowebcache.diskquota.jdbc.PostgreSQLDialect;
import org.geowebcache.diskquota.jdbc.SQLDialect;
import org.geowebcache.testcontainers.jdbc.PostgresContainer;
import org.junit.ClassRule;

/**
 * Runs {@link AbstractJDBCQuotaStoreConcurrencyTest} against a PostgreSQL testcontainer; this is where the retry layer
 * is most exercised, since Postgres SSI results in serialization aborts under contention.
 */
public class PostgreSQLQuotaStoreConcurrencyIT extends AbstractJDBCQuotaStoreConcurrencyTest {

    @ClassRule
    public static final PostgresContainer POSTGRES = PostgresContainer.latest().disabledWithoutDocker();

    @Override
    protected DataSource newDataSource() {
        return newPooledDataSource(
                POSTGRES.getDriverClassName(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Override
    protected SQLDialect newDialect() {
        return new PostgreSQLDialect();
    }
}
