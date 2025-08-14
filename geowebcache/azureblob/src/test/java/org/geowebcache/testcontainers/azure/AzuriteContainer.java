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
 * <p>Copyright 2024
 */
package org.geowebcache.testcontainers.azure;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.apache.commons.lang3.StringUtils;
import org.geowebcache.azure.AzureBlobStoreData;
import org.geowebcache.azure.tests.container.AzuriteAzureBlobStoreConformanceIT;
import org.junit.Assume;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * <a href="https://java.testcontainers.org/">Testcontainers</a> container for AWS <a href=
 * "https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite">Azurite</a> Azure Storage Emulator.
 *
 * <p>Runs the <a href=
 * "https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite?tabs=docker-hub%2Cblob-storage">Azurite
 * Docker image</a> for local Azure Storage development with testcontainers.
 *
 * <p>Azurite sets up the following well-known account and key for the Azure Storage Emulator, available through
 * {@link #getAccountName()} and {@link #getAccountKey()}:
 *
 * <ul>
 *   <li>Account name: {@code devstoreaccount1}
 *   <li>Account key: {@code Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==}
 * </ul>
 *
 * <p>Usage: For Junit 4, use it as a {@code @Rule} or {@code @ClassRule}:
 *
 * <pre>
 * <code>
 *   @Rule public AzuriteContainer azurite = AzuriteContainer.latest();
 * </code>
 * </pre>
 *
 * works with the latest {@code com.azure:azure-storage-blob:jar:12.27.0} as a dependency.
 *
 * <p>Sample test:
 *
 * <pre>
 * <code>
 *   @ClassRule public static AzuriteContainer azurite = AzuriteContainer.latest();
 *
 *   @Test
 *   public void azureBlobStoreSmokeTest(){
 *      String container = "testcontainer";//ought to be lower case
 *      AzureBlobStoreData config = azurite.getConfiguration(container);
 *      AzureBlobStore store = new AzureBlobStore(config, tileLayerDispatcher, lockProvider);
 *      assertFalse(store.layerExists("layer1");
 *   }
 * </code>
 * </pre>
 */
public class AzuriteContainer extends GenericContainer<AzuriteContainer> {

    private static final DockerImageName LATEST_IMAGE =
            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest");

    private final String accountName = "devstoreaccount1";
    private final String accountKey =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    private final int blobsPort = 10_000;

    /** flag for {@link #disabledWithoutDocker()} */
    private boolean disabledWithoutDocker;

    private AzuriteContainer(DockerImageName imageName) {
        super(imageName);
        super.setWaitStrategy(Wait.forListeningPort());
        super.addExposedPort(blobsPort);
    }

    /** @return a container running {@code mcr.microsoft.com/azure-storage/azurite:latest} */
    public static AzuriteContainer latest() {
        return new AzuriteContainer(LATEST_IMAGE);
    }

    /**
     * Disables the tests using this testcontainer if there's no Docker environment available.
     *
     * <p>Same effect as JUnit 5's {@code org.testcontainers.junit.jupiter.@Testcontainers(disabledWithoutDocker =
     * true)}
     */
    public AzuriteContainer disabledWithoutDocker() {
        this.disabledWithoutDocker = true;
        return this;
    }

    /**
     * Overrides to apply the {@link Assume assumption} checking the Docker environment is available if
     * {@link #disabledWithoutDocker() enabled}, so this test container can be used as a {@code ClassRule @ClassRule}
     * and hence avoid running a container for each test case.
     */
    @Override
    @SuppressWarnings("deprecation")
    public Statement apply(Statement base, Description description) {
        if (disabledWithoutDocker) {
            assumeTrue(
                    "Docker environment unavailable, ignoring test "
                            + AzuriteAzureBlobStoreConformanceIT.class.getSimpleName(),
                    DockerClientFactory.instance().isDockerAvailable());
        }
        return super.apply(base, description);
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountKey() {
        return accountKey;
    }

    /**
     * Returns the localhost port where the azurite blob storage service is running.
     *
     * <p>when in {@link #legacy() legacy} mode, a small http proxy is run and the proxy port is returned. The proxy
     * fixes some protocol issues. For instance, re-writes the returned response headers {@code etag},
     * {@code last-modified}, and {@code content-type}, as {@code Etag}, {@code Last-Modified}, and
     * {@code Content-Type}, respectively, as expected by the Netty version the legacy
     * {@code com.microsoft.azure:azure-storage-blob} dependency transitively carries over.
     */
    public int getBlobsPort() {
        return super.getMappedPort(blobsPort);
    }

    public String getBlobServiceUrl() {
        return "http://localhost:%d/%s".formatted(getBlobsPort(), getAccountName());
    }

    public AzureBlobStoreData getConfiguration(String container) {
        assertTrue("Container must be lower case", StringUtils.isAllLowerCase(container));
        AzureBlobStoreData config = new AzureBlobStoreData();
        config.setServiceURL(getBlobServiceUrl());
        config.setAccountName(getAccountName());
        config.setAccountKey(getAccountKey());
        config.setMaxConnections(10);
        config.setContainer(container);
        return config;
    }
}
