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
 * @author Gabriel Roldan, Camptocamp, Copyright 2025
 */
package org.geowebcache.storage.blobstore.gcs;

import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.aiven.testcontainers.fakegcsserver.FakeGcsServerContainer;
import javax.annotation.Nullable;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.StorageException;
import org.junit.Assume;
import org.junit.rules.ExternalResource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

public class GoogleCloudStorageContainerSupport extends ExternalResource {

    private static final String BUCKET_NAME = "test-bucket";
    private static final String PROJECT_ID = "test-project";

    public static final DockerImageName IMAGE_NAME = DockerImageName.parse("fsouza/fake-gcs-server:latest");

    private FakeGcsServerContainer gcsEmulator;

    private String emulatorEndpoint;

    @Override
    protected void after() {
        if (gcsEmulator != null) {
            gcsEmulator.stop();
            gcsEmulator = null;
        }
    }

    @Override
    protected void before() throws Throwable {
        // Skip tests if Docker is not available
        Assume.assumeTrue(
                "Docker is not available, skipping Testcontainers tests.",
                DockerClientFactory.instance().isDockerAvailable());

        gcsEmulator = new FakeGcsServerContainer(IMAGE_NAME);

        // Wait for the emulator to be ready
        gcsEmulator.start();

        // Configure the Storage client to use the emulator
        String emulatorHost = gcsEmulator.getHost();
        Integer emulatorPort = gcsEmulator.getFirstMappedPort();
        emulatorEndpoint = "http://" + emulatorHost + ":" + emulatorPort;

        // Create Storage client
        try (Storage storage = StorageOptions.newBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorEndpoint)
                .build()
                .getService()) {

            // Create a bucket
            BucketInfo bucketInfo = BucketInfo.newBuilder(BUCKET_NAME).build();
            storage.create(bucketInfo);
        }
    }

    public String getEmulatorEndpoint() {
        return emulatorEndpoint;
    }

    public String getBucket() {
        return BUCKET_NAME;
    }

    public String getProjectId() {
        return PROJECT_ID;
    }

    public GoogleCloudStorageBlobStoreInfo createBlobstoreInfo() {
        GoogleCloudStorageBlobStoreInfo info = new GoogleCloudStorageBlobStoreInfo();

        info.setEndpointUrl(getEmulatorEndpoint());
        info.setBucket(getBucket());
        info.setProjectId(getProjectId());
        return info;
    }

    public GoogleCloudStorageBlobStoreInfo createBlobstoreInfo(String prefix) {
        GoogleCloudStorageBlobStoreInfo info = createBlobstoreInfo();
        info.setPrefix(prefix);
        return info;
    }

    public GoogleCloudStorageBlobStore createBlobStore(TileLayerDispatcher layers) throws StorageException {
        return createBlobStore(null, layers);
    }

    /**
     * @param prefix the blob name prefix applied to the cache, if {@code null}, the cache operates at the bucket's root
     * @param layers
     * @return
     * @throws StorageException
     */
    public GoogleCloudStorageBlobStore createBlobStore(@Nullable String prefix, TileLayerDispatcher layers)
            throws StorageException {
        GoogleCloudStorageClient client = createClient(prefix);

        return new GoogleCloudStorageBlobStore(client, layers);
    }

    public GoogleCloudStorageClient createClient(String prefix) throws StorageException {
        GeoWebCacheEnvironment env = new GeoWebCacheEnvironment();
        GoogleCloudStorageBlobStoreInfo info = createBlobstoreInfo(prefix);
        return GoogleCloudStorageClient.builder(info, env).build();
    }
}
