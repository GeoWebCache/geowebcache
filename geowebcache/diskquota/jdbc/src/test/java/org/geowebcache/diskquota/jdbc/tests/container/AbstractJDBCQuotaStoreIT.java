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

import java.util.Properties;
import org.geowebcache.diskquota.jdbc.JDBCQuotaStoreTest;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Abstract base for {@link JDBCQuotaStoreTest} subclasses that source their JDBC connection from a Testcontainers
 * {@link JdbcDatabaseContainer} instead of the legacy {@code ~/.geowebcache/<fixture>.properties} file.
 *
 * <p>The whole {@link JDBCQuotaStoreTest} suite is reused as-is: the only deviation is that this base overrides the
 * inner fixture rule to short-circuit the fixture-file lookup with {@link #containerFixture()}, which derives the
 * connection {@link Properties} from the running container provided by {@link #getContainer()}.
 *
 * <p>Subclasses are picked up by Failsafe (their names end in {@code IT}). Surefire ignores them, so the existing
 * fixture-based subclasses keep their {@code Test}-suffix convention.
 */
public abstract class AbstractJDBCQuotaStoreIT extends JDBCQuotaStoreTest {

    /**
     * @return the started {@link JdbcDatabaseContainer} the IT runs against. Subclasses typically expose it as a
     *     {@code @ClassRule} so it spins up once per test class.
     */
    protected abstract JdbcDatabaseContainer<?> getContainer();

    /**
     * Builds the {@link Properties} the base {@code JDBCFixtureRule} expects. Sourced from the container so that no
     * filesystem fixture is needed.
     */
    @SuppressWarnings("PMD.CloseResource")
    protected Properties containerFixture() {
        JdbcDatabaseContainer<?> container = getContainer();
        Properties fixture = new Properties();
        fixture.put("driver", container.getDriverClassName());
        fixture.put("url", container.getJdbcUrl());
        fixture.put("username", container.getUsername());
        fixture.put("password", container.getPassword());
        return fixture;
    }

    @Override
    protected JDBCFixtureRule makeFixtureRule() {
        return new JDBCFixtureRule(getFixtureId()) {
            @Override
            protected Properties createOfflineFixture() {
                return containerFixture();
            }
        };
    }
}
