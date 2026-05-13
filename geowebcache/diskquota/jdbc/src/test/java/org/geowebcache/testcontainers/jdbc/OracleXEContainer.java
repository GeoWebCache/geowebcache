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
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * <a href="https://java.testcontainers.org/modules/databases/oraclexe/">Testcontainers</a> container for Oracle Express
 * Edition, used by the JDBC disk quota store integration tests.
 *
 * <p>Backed by the community {@code gvenzl/oracle-xe:21-slim-faststart} image, which trims the official Oracle XE
 * distribution to a CI-friendly size (~1.4 GB) and starts in under a minute.
 *
 * <p>Usage as a {@code @ClassRule}:
 *
 * <pre>
 * <code>
 *   &#64;ClassRule public static OracleXEContainer oracle = OracleXEContainer.latest().disabledWithoutDocker();
 * </code>
 * </pre>
 *
 * <p>Mirrors the {@link PostgresContainer} / AzuriteContainer pattern: the {@link #disabledWithoutDocker()
 * disabledWithoutDocker} flag arranges for the test class to be {@link Assume assumed} away cleanly when no Docker
 * environment is available, instead of erroring at container start.
 */
public class OracleXEContainer extends OracleContainer {

    private static final DockerImageName LATEST_IMAGE =
            DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart").asCompatibleSubstituteFor("gvenzl/oracle-xe");

    private boolean disabledWithoutDocker;

    public OracleXEContainer(DockerImageName imageName) {
        super(imageName);
    }

    /** @return a container running {@code gvenzl/oracle-xe:21-slim-faststart} with the testcontainers defaults */
    public static OracleXEContainer latest() {
        return new OracleXEContainer(LATEST_IMAGE);
    }

    /**
     * Skips tests using this container if no Docker environment is available, instead of failing them.
     *
     * <p>Same effect as JUnit 5's {@code @Testcontainers(disabledWithoutDocker = true)}.
     */
    public OracleXEContainer disabledWithoutDocker() {
        this.disabledWithoutDocker = true;
        return this;
    }

    /**
     * Overrides {@link OracleContainer#apply} to apply the Docker-availability {@link Assume assumption} before the
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
