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

import org.geowebcache.diskquota.jdbc.PostgreSQLDialect;
import org.geowebcache.diskquota.jdbc.SQLDialect;
import org.geowebcache.testcontainers.jdbc.PostgresContainer;
import org.junit.ClassRule;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Runs the full {@code JDBCQuotaStoreTest} suite against a real PostgreSQL via Testcontainers.
 *
 * <p>Failsafe picks this up via the {@code *IT} naming convention. If Docker is unavailable the class is skipped
 * cleanly through {@link PostgresContainer#disabledWithoutDocker()}.
 */
public class PostgreSQLQuotaStoreIT extends AbstractJDBCQuotaStoreIT {

    @ClassRule
    public static final PostgresContainer POSTGRES = PostgresContainer.latest().disabledWithoutDocker();

    @Override
    protected SQLDialect getDialect() {
        return new PostgreSQLDialect();
    }

    @Override
    protected String getFixtureId() {
        return "postgresql-testcontainer";
    }

    @Override
    protected JdbcDatabaseContainer<?> getContainer() {
        return POSTGRES;
    }
}
