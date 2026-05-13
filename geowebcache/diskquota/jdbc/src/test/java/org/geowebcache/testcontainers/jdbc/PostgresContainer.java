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
package org.geowebcache.testcontainers.jdbc;

import static org.junit.Assume.assumeTrue;

import org.junit.Assume;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * <a href="https://java.testcontainers.org/">Testcontainers</a> container for PostgreSQL, used by the JDBC disk quota
 * store integration tests.
 *
 * <p>Usage as a {@code @ClassRule}:
 *
 * <pre>
 * <code>
 *   &#64;ClassRule public static PostgresContainer postgres = PostgresContainer.latest().disabledWithoutDocker();
 * </code>
 * </pre>
 *
 * <p>Mirrors the {@code AzuriteContainer} pattern used elsewhere in this repository: the
 * {@link #disabledWithoutDocker() disabledWithoutDocker} flag arranges for the test class to be {@link Assume assumed}
 * away cleanly when no Docker environment is available, instead of erroring at container start.
 */
public class PostgresContainer extends PostgreSQLContainer<PostgresContainer> {

    private static final DockerImageName LATEST_IMAGE = DockerImageName.parse("postgres:16-alpine");

    private boolean disabledWithoutDocker;

    public PostgresContainer(DockerImageName imageName) {
        super(imageName);
    }

    /** @return a container running {@code postgres:16-alpine} with default {@code test}/{@code test} credentials */
    public static PostgresContainer latest() {
        return new PostgresContainer(LATEST_IMAGE);
    }

    /**
     * Skips tests using this container if no Docker environment is available, instead of failing them.
     *
     * <p>Same effect as JUnit 5's {@code @Testcontainers(disabledWithoutDocker = true)}.
     */
    public PostgresContainer disabledWithoutDocker() {
        this.disabledWithoutDocker = true;
        return this;
    }

    /**
     * Overrides {@link PostgreSQLContainer#apply} to apply the Docker-availability {@link Assume assumption} before the
     * container is started, so that the test class is skipped cleanly when Docker is unavailable.
     */
    @Override
    @SuppressWarnings("deprecation")
    public Statement apply(Statement base, Description description) {
        if (disabledWithoutDocker) {
            assumeTrue(
                    "Docker environment unavailable, ignoring " + description.getDisplayName(),
                    DockerClientFactory.instance().isDockerAvailable());
        }
        return super.apply(base, description);
    }
}
