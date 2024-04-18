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
 * @author Andrea Aime, GeoSolutions, Copyright 2019
 */
package org.geowebcache.azure;

import com.microsoft.azure.storage.blob.BlockBlobURL;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.DownloadResponse;
import com.microsoft.azure.storage.blob.ListBlobsOptions;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.SharedKeyCredentials;
import com.microsoft.azure.storage.blob.StorageURL;
import com.microsoft.azure.storage.blob.models.BlobFlatListSegment;
import com.microsoft.azure.storage.blob.models.BlobHTTPHeaders;
import com.microsoft.azure.storage.blob.models.BlobItem;
import com.microsoft.azure.storage.blob.models.ContainerListBlobFlatSegmentResponse;
import com.microsoft.rest.v2.RestException;
import com.microsoft.rest.v2.http.HttpClient;
import com.microsoft.rest.v2.http.HttpClientConfiguration;
import com.microsoft.rest.v2.http.NettyClient;
import com.microsoft.rest.v2.http.SharedChannelPoolOptions;
import com.microsoft.rest.v2.util.FlowableUtil;
import io.netty.bootstrap.Bootstrap;
import io.reactivex.Flowable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nullable;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.URLs;
import org.springframework.http.HttpStatus;

class AzureClient implements Closeable {

    private final NettyClient.Factory factory;
    private AzureBlobStoreData configuration;
    private final ContainerURL container;

    public AzureClient(AzureBlobStoreData configuration) throws StorageException {
        this.configuration = configuration;
        try {
            SharedKeyCredentials creds =
                    new SharedKeyCredentials(
                            configuration.getAccountName(), configuration.getAccountKey());

            // setup the HTTPClient, keep the factory on the side to close it down on destroy
            factory =
                    new NettyClient.Factory(
                            new Bootstrap(),
                            0,
                            new SharedChannelPoolOptions()
                                    .withPoolSize(configuration.getMaxConnections()),
                            null);
            final HttpClient client;
            Proxy proxy = configuration.getProxy();
            // not clear how to use credentials for proxy,
            // https://github.com/Azure/autorest-clientruntime-for-java/issues/624
            if (proxy != null) {
                HttpClientConfiguration clientConfiguration = new HttpClientConfiguration(proxy);
                client = factory.create(clientConfiguration);
            } else {
                client = factory.create(null);
            }

            // build the container access
            PipelineOptions options = new PipelineOptions().withClient(client);
            ServiceURL serviceURL =
                    new ServiceURL(
                            URLs.of(getServiceURL(configuration)),
                            StorageURL.createPipeline(creds, options));

            String containerName = configuration.getContainer();
            this.container = serviceURL.createContainerURL(containerName);
            // no way to see if the containerURL already exists, try to create and see if
            // we get a 409 CONFLICT
            try {
                int status = this.container.getProperties().blockingGet().statusCode();
                if (status == HttpStatus.NOT_FOUND.value()) {
                    status = this.container.create(null, null, null).blockingGet().statusCode();
                    if (!HttpStatus.valueOf(status).is2xxSuccessful()
                            && status != HttpStatus.CONFLICT.value()) {
                        throw new StorageException(
                                "Failed to create container "
                                        + containerName
                                        + ", REST API returned a "
                                        + status);
                    }
                }
            } catch (RestException e) {
                if (e.response().statusCode() != HttpStatus.CONFLICT.value()) {
                    throw new StorageException("Failed to create container", e);
                }
            }
        } catch (Exception e) {
            throw new StorageException("Failed to setup Azure connection and container", e);
        }
    }

    public String getServiceURL(AzureBlobStoreData configuration) {
        String serviceURL = configuration.getServiceURL();
        if (serviceURL == null) {
            // default to account name based location
            serviceURL =
                    (configuration.isUseHTTPS() ? "https" : "http")
                            + "://"
                            + configuration.getAccountName()
                            + ".blob.core.windows.net";
        }
        return serviceURL;
    }

    /**
     * Returns a blob for the given key (may not exist yet, and in need of being created)
     *
     * @param key The blob key
     */
    public BlockBlobURL getBlockBlobURL(String key) {
        return container.createBlockBlobURL(key);
    }

    @Nullable
    public byte[] getBytes(String key) throws StorageException {
        BlockBlobURL blob = getBlockBlobURL(key);
        try {
            DownloadResponse response = blob.download().blockingGet();
            ByteBuffer buffer =
                    FlowableUtil.collectBytesInBuffer(response.body(null)).blockingGet();
            byte[] result = new byte[buffer.remaining()];
            buffer.get(result);
            return result;
        } catch (RestException e) {
            if (e.response().statusCode() == 404) {
                return null;
            }
            throw new StorageException("Failed to retreive bytes for " + key, e);
        }
    }

    public Properties getProperties(String key) throws StorageException {
        Properties properties = new Properties();
        byte[] bytes = getBytes(key);
        if (bytes != null) {
            try {
                properties.load(
                        new InputStreamReader(
                                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return properties;
    }

    public void putProperties(String resourceKey, Properties properties) throws StorageException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            properties.store(out, "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            BlockBlobURL blob = getBlockBlobURL(resourceKey);
            byte[] bytes = out.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            BlobHTTPHeaders headers = new BlobHTTPHeaders();
            headers.withBlobContentType("text/plain");

            int status =
                    blob.upload(Flowable.just(buffer), bytes.length, headers, null, null, null)
                            .blockingGet()
                            .statusCode();
            if (!HttpStatus.valueOf(status).is2xxSuccessful()) {
                throw new StorageException(
                        "Upload request failed with status "
                                + status
                                + " on resource "
                                + resourceKey);
            }
        } catch (RestException e) {
            throw new StorageException("Failed to update e property file at " + resourceKey, e);
        }
    }

    public List<BlobItem> listBlobs(String prefix, Integer maxResults) {
        ContainerListBlobFlatSegmentResponse response =
                container
                        .listBlobsFlatSegment(
                                null,
                                new ListBlobsOptions()
                                        .withPrefix(prefix)
                                        .withMaxResults(maxResults))
                        .blockingGet();

        BlobFlatListSegment segment = response.body().segment();
        List<BlobItem> items = new ArrayList<>();
        if (segment != null) {
            items.addAll(segment.blobItems());
        }
        return items;
    }

    @Override
    public void close() {
        factory.close();
    }

    public String getContainerName() {
        return configuration.getContainer();
    }

    public ContainerURL getContainer() {
        return container;
    }
}
