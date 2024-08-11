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
package org.geowebcache.testcontainers.azure;

import static java.lang.String.format;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.UncheckedException;
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
 * "https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite">Azurite</a>
 * blobstore test environment.
 *
 * <p>Runs the <a href=
 * "https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite?tabs=docker-hub%2Cblob-storage">Azurite
 * Docker image</a> for local Azure Storage development with testcontainers.
 *
 * <p>Azurite accepts the same well-known account and key used by the legacy Azure Storage Emulator:
 *
 * <ul>
 *   <li>Account name: {@code devstoreaccount1}
 *   <li>Account key: {@code
 *       Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==}
 * </ul>
 *
 * <p>Usage: For Junit 4, use it as a {@code @Rule} or {@code @ClassRule}:
 *
 * <pre>
 * <code>
 *   @Rule public AzuriteContainer azurite = AzuriteContainer.legacy();
 * </code>
 * </pre>
 *
 * works with the old {@code com.microsoft.azure:azure-storage-blob:jar:11.0.0} as a dependency.
 *
 * <pre>
 * <code>
 *   @Rule public AzuriteContainer azurite = AzuriteContainer.legacy();
 * </code>
 * </pre>
 *
 * works with the latest {@code com.azure:azure-storage-blob:jar:12.27.0} as a dependency.
 *
 * <p>Sample test:
 *
 * <pre>
 * <code>
 *   @ClassRule public static AzuriteContainer azurite = AzuriteContainer.legacy();
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

    private static final DockerImageName LEGACY_IMAGE =
            DockerImageName.parse("arafato/azurite:2.6.5");

    private final String accountName = "devstoreaccount1";
    private final String accountKey =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    private final int blobsPort = 10_000;

    private AzuriteContainerLegacyProxy proxy;

    private final boolean doProxy;

    /** Whether to print request/response debug information when in {@link #legacy} mode */
    private boolean debugRequests;

    /** flag for {@link #disabledWithoutDocker()} */
    private boolean disabledWithoutDocker;

    private AzuriteContainer(DockerImageName imageName, boolean doProxy) {
        super(imageName);
        this.doProxy = doProxy;
        super.setWaitStrategy(Wait.forListeningPort());
        super.addExposedPort(blobsPort);
    }

    /**
     * @return a container running {@code arafato/azurite:2.6.5} and {@link #getBlobsPort() proxied}
     *     to fix protocol discrepancies so it works correctly with older {@code
     *     com.microsoft.azure:azure-storage-blob} dependencies
     */
    public static AzuriteContainer legacy() {
        return new AzuriteContainer(LEGACY_IMAGE, true);
    }

    /** @return a container running {@code mcr.microsoft.com/azure-storage/azurite:latest} */
    public static AzuriteContainer latest() {
        return new AzuriteContainer(LATEST_IMAGE, false);
    }

    /**
     * Enables request/response debugging when in legacy mode
     *
     * <p>Sample output:
     *
     * <pre>
     * <code>
     * routing GET http://localhost:44445/devstoreaccount1/testputgetblobisnotbytearrayresource/topp%3Aworld%2FEPSG%3A4326%2Fpng%2Fdefault%2F12%2F20%2F30.png to GET http://localhost:33319/devstoreaccount1/testputgetblobisnotbytearrayresource/topp%3Aworld%2FEPSG%3A4326%2Fpng%2Fdefault%2F12%2F20%2F30.png
     * 	applied request header Authorization: SharedKey devstoreaccount1:6UeSk1Qf8XRibLI1sE3tasmDxOtVxGUSMDQqRUDIW9Y=
     * 	applied request header x-ms-version: 2018-11-09
     * 	applied request header x-ms-date: Fri, 09 Aug 2024 17:08:38 GMT
     * 	applied request header host: localhost
     * 	applied request header x-ms-client-request-id: 526b726a-13af-49a3-b277-fdf645d77903
     * 	applied request header User-Agent: Azure-Storage/11.0.0 (JavaJRE 11.0.23; Linux 6.8.0-39-generic)
     * 	response: 200 OK
     * 	applied response header X-Powered-By: Express
     * 	applied response header ETag: "jzUOHaHcch36ue3TFspQaLiWSvo"
     * 	applied response header Last-Modified: Fri, 09 Aug 2024 17:08:38 GMT
     * 	applied response header x-ms-version: 2016-05-31
     * 	applied response header date: Fri, 09 Aug 2024 17:08:38 GMT
     * 	applied response header x-ms-request-id: 05130dd1-5672-11ef-a96b-c7f08f042b95
     * 	applied response header accept-ranges: bytes
     * 	applied response header x-ms-blob-type: BlockBlob
     * 	applied response header x-ms-request-server-encrypted: false
     * 	applied response header Content-Type: image/png
     * 	Content-Type: image/png
     * </code>
     * </pre>
     */
    public AzuriteContainer debugLegacy() {
        this.debugRequests = true;
        return this;
    }

    /**
     * Disables the tests using this testcontainer if there's no Docker environment available.
     *
     * <p>Same effect as JUnit 5's {@code
     * org.testcontainers.junit.jupiter.@Testcontainers(disabledWithoutDocker = true)}
     */
    public AzuriteContainer disabledWithoutDocker() {
        this.disabledWithoutDocker = true;
        return this;
    }

    /**
     * Overrides to apply the {@link Assume assumption} checking the Docker environment is available
     * if {@link #disabledWithoutDocker() enabled}, so this test container can be used as a {@code
     * ClassRule @ClassRule} and hence avoid running a container for each test case.
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

    @Override
    public void start() {
        super.start();
        if (doProxy && proxy == null) {
            int targetPort = getRealBlobsPort();
            proxy = new AzuriteContainerLegacyProxy(targetPort).debugRequests(debugRequests);
            try {
                proxy.start();
            } catch (IOException e) {
                throw new UncheckedException(e);
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (doProxy && null != proxy) {
            try {
                proxy.stop();
            } finally {
                proxy = null;
            }
        }
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
     * <p>when in {@link #legacy() legacy} mode, a small http proxy is run and the proxy port is
     * returned. The proxy fixes some protocol issues. For instance, re-writes the returned response
     * headers {@code etag}, {@code last-modified}, and {@code content-type}, as {@code Etag},
     * {@code Last-Modified}, and {@code Content-Type}, respectively, as expected by the Netty
     * version the legacy {@code com.microsoft.azure:azure-storage-blob} dependency transitively
     * carries over.
     */
    public int getBlobsPort() {
        if (doProxy) {
            if (proxy == null) throw new IllegalStateException("");
            return proxy.getLocalPort();
        }
        return getRealBlobsPort();
    }

    int getRealBlobsPort() {
        return super.getMappedPort(blobsPort);
    }

    public String getBlobServiceUrl() {
        return format("http://localhost:%d/%s", getBlobsPort(), getAccountName());
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
