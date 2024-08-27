/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2024
 */
package org.geowebcache.azure.tests.container;

import org.geowebcache.azure.AzureBlobStoreConformanceTest;
import org.geowebcache.azure.AzureBlobStoreData;
import org.geowebcache.testcontainers.azure.AzuriteContainer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestName;

/**
 * Runs {@link AzureBlobStoreConformanceTest} tests against a local ephemeral Docker container using
 * {@link AzuriteContainer}.
 *
 * <p>If there's no Docker environment, the test is {@link AzuriteContainer#disabledWithoutDocker()
 * ignored}
 */
public class AzuriteAzureBlobStoreConformanceIT extends AzureBlobStoreConformanceTest {

    /**
     * Use "legacy" container to work with {@literal
     * com.microsoft.azure:azure-storage-blob:jar:11.0.0}. Instantiate it as
     * AzuriteContainer.legacy().debugLegacy() to print out request/response information for
     * debugging purposes
     */
    @ClassRule
    public static AzuriteContainer azurite = AzuriteContainer.legacy().disabledWithoutDocker();

    /** Used to get a per-test case Azure container */
    @Rule public TestName testName = new TestName();

    @Override
    protected AzureBlobStoreData getConfiguration() {
        // container must be lower case or we get a 400 bad request
        String container = testName.getMethodName().toLowerCase();
        return azurite.getConfiguration(container);
    }
}
