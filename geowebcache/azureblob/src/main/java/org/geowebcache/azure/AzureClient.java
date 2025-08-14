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
 * @author Andrea Aime, GeoSolutions, Copyright 2019
 */
package org.geowebcache.azure;

import com.azure.core.credential.AzureNamedKeyCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.util.BinaryData;
import com.azure.core.util.ClientOptions;
import com.azure.core.util.Context;
import com.azure.core.util.HttpClientOptions;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobDownloadContentResponse;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.options.BlockBlobSimpleUploadOptions;
import com.azure.storage.blob.specialized.BlockBlobClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.geowebcache.storage.StorageException;
import org.springframework.http.HttpStatus;

public class AzureClient {

    private AzureBlobStoreData configuration;
    private final BlobContainerClient container;

    public AzureClient(AzureBlobStoreData configuration) throws StorageException {
        this.configuration = configuration;

        try {
            BlobServiceClient serviceClient = createBlobServiceClient(configuration);

            String containerName = configuration.getContainer();
            this.container = getOrCreateContainer(serviceClient, containerName);
        } catch (StorageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new StorageException("Failed to setup Azure connection and container", e);
        }
    }

    BlobContainerClient getOrCreateContainer(BlobServiceClient serviceClient, String containerName)
            throws StorageException {

        BlobContainerClient container = serviceClient.getBlobContainerClient(containerName);
        if (!container.exists()) {
            // use createIfNotExists() instead of create() in case multiple instances are
            // starting up at the same time
            boolean created = container.createIfNotExists();
            if (!created && !container.exists()) {
                throw new StorageException("Failed to create container " + containerName);
            }
        }
        return container;
    }

    BlobServiceClient createBlobServiceClient(AzureBlobStoreData configuration) {
        String serviceURL = getServiceURL(configuration);
        AzureNamedKeyCredential creds = getCredentials(configuration);
        ClientOptions clientOpts = new ClientOptions();
        HttpClient httpClient = createHttpClient(configuration);
        BlobServiceClientBuilder builder = new BlobServiceClientBuilder()
                .endpoint(serviceURL)
                .clientOptions(clientOpts)
                .httpClient(httpClient);
        if (null != creds) {
            builder = builder.credential(creds);
        }
        return builder.buildClient();
    }

    AzureNamedKeyCredential getCredentials(AzureBlobStoreData configuration) {
        String accountName = configuration.getAccountName();
        String accountKey = configuration.getAccountKey();
        if (null != accountName && null != accountKey) {
            return new AzureNamedKeyCredential(accountName, accountKey);
        }
        return null;
    }

    HttpClient createHttpClient(AzureBlobStoreData blobStoreConfig) {

        @Nullable Integer maxConnections = blobStoreConfig.getMaxConnections();
        @Nullable ProxyOptions proxyOptions = getProxyOptions(blobStoreConfig);

        HttpClientOptions opts = new HttpClientOptions();
        opts.setProxyOptions(proxyOptions);
        opts.setMaximumConnectionPoolSize(maxConnections);
        return HttpClient.createDefault(opts);
    }

    ProxyOptions getProxyOptions(AzureBlobStoreData blobStoreConfig) {
        ProxyOptions proxyOptions = null;
        Proxy proxy = blobStoreConfig.getProxy();
        if (null != proxy) {
            ProxyOptions.Type type = ProxyOptions.Type.HTTP;
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            String proxyUsername = blobStoreConfig.getProxyUsername();
            String proxyPassword = blobStoreConfig.getProxyPassword();

            proxyOptions = new ProxyOptions(type, address);
            proxyOptions.setCredentials(proxyUsername, proxyPassword);
        }
        return proxyOptions;
    }

    String getServiceURL(AzureBlobStoreData configuration) {
        String serviceURL = configuration.getServiceURL();
        if (serviceURL == null) {
            // default to account name based location
            String proto = configuration.isUseHTTPS() ? "https" : "http";
            String account = configuration.getAccountName();
            serviceURL = "%s://%s.blob.core.windows.net".formatted(proto, account);
        }
        return serviceURL;
    }

    /**
     * Returns a blob for the given key (may not exist yet, and in need of being created)
     *
     * @param key The blob key
     * @return
     */
    public BlockBlobClient getBlockBlobClient(String key) {
        BlobClient blobClient = container.getBlobClient(key);
        return blobClient.getBlockBlobClient();
    }

    /**
     * @return the blob's download response, or {@code null} if not found
     * @throws BlobStorageException
     */
    public BlobDownloadContentResponse download(String key) {
        BlobClient blobClient = container.getBlobClient(key);
        DownloadRetryOptions options = new DownloadRetryOptions().setMaxRetryRequests(0);
        BlobRequestConditions conditions = null;
        Duration timeout = null;
        Context context = Context.NONE;
        try {
            return blobClient.downloadContentWithResponse(options, conditions, timeout, context);
        } catch (BlobStorageException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                return null;
            }
            throw e;
        }
    }

    /** @throws BlobStorageException If an I/O error occurs. */
    @Nullable
    public byte[] getBytes(String key) {
        BlobDownloadContentResponse download = download(key);
        return download == null ? null : download.getValue().toBytes();
    }

    public Properties getProperties(String key) {
        Properties properties = new Properties();
        byte[] bytes = getBytes(key);
        if (bytes != null) {
            try {
                properties.load(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return properties;
    }

    public void putProperties(String resourceKey, Properties properties) throws StorageException {

        String contentType = "text/plain";
        BinaryData data = toBinaryData(properties);

        try {
            upload(resourceKey, data, contentType);
        } catch (StorageException e) {
            throw new StorageException("Failed to update e property file at " + resourceKey, e.getCause());
        }
    }

    public void upload(String resourceKey, BinaryData data, String contentType) throws StorageException {

        BlockBlobSimpleUploadOptions upload = new BlockBlobSimpleUploadOptions(data);
        upload.setHeaders(new BlobHttpHeaders().setContentType(contentType));
        Duration timeout = null;
        Context context = Context.NONE;

        Response<BlockBlobItem> response;
        try {
            BlockBlobClient blob = getBlockBlobClient(resourceKey);
            response = blob.uploadWithResponse(upload, timeout, context);
        } catch (UncheckedIOException e) {
            throw new StorageException("Failed to upload blob " + resourceKey, e.getCause());
        }
        int status = response.getStatusCode();
        if (!HttpStatus.valueOf(status).is2xxSuccessful()) {
            throw new StorageException("Upload request failed with status " + status + " on resource " + resourceKey);
        }
    }

    private BinaryData toBinaryData(Properties properties) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            properties.store(out, "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return BinaryData.fromBytes(out.toByteArray());
    }

    public boolean blobExists(String key) {
        return getBlockBlobClient(key).exists();
    }

    public boolean prefixExists(String prefix) {
        return listBlobs(prefix, 1).findFirst().isPresent();
    }

    /**
     * @param prefix
     * @return an internally paged Stream, with the default items-per-page setting of
     *     {@link ListBlobsOptions#getMaxResultsPerPage() 5000}
     */
    public Stream<BlobItem> listBlobs(String prefix) {
        return listBlobs(prefix, 5_000);
    }

    /**
     * @param prefix
     * @param maxResultsPerPage used to control how many items Azure will return per page. It's not the max results to
     *     return entirely, so use it with caution.
     * @return an internally paged (as per {@code maxResultsPerPage}) Stream.
     */
    public Stream<BlobItem> listBlobs(String prefix, int maxResultsPerPage) {
        ListBlobsOptions opts = new ListBlobsOptions().setPrefix(prefix);

        // if > 5000, Azure will return 5000 items per page
        opts.setMaxResultsPerPage(maxResultsPerPage);

        // An optional timeout value beyond which a {@link RuntimeException} will be
        // raised. revisit: set a timeout?
        Duration timeout = null;
        PagedIterable<BlobItem> blobs = container.listBlobs(opts, timeout);
        return blobs.stream();
    }

    public String getContainerName() {
        return configuration.getContainer();
    }

    public BlobContainerClient getContainer() {
        return container;
    }

    public boolean deleteBlob(String key) {
        BlockBlobClient metadata = getBlockBlobClient(key);
        return metadata.deleteIfExists();
    }
}
