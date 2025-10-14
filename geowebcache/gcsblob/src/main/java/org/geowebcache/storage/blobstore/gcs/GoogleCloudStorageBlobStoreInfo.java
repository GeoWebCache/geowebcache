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

import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.StorageException;
import org.springframework.util.StringUtils;

/**
 * Configuration and factory for a {@link GoogleCloudStorageBlobStore}.
 *
 * <p>This class holds the necessary connection and authentication details for accessing a Google Cloud Storage bucket.
 * It is typically configured in {@code geowebcache.xml} and used by GeoWebCache to instantiate the blob store.
 *
 * @implNote All configuration properties are strings to allow for environment parameterization (e.g., using
 *     {@code ${name}} variable placeholders). The
 *     {@link GoogleCloudStorageClient#builder(GoogleCloudStorageBlobStoreInfo, GeoWebCacheEnvironment)} dereferences
 *     these placeholders.
 * @since 1.28
 */
@SuppressWarnings("serial")
public class GoogleCloudStorageBlobStoreInfo extends BlobStoreInfo {

    private String projectId;
    private String quotaProjectId;
    private String bucket;
    private String prefix;
    private String endpointUrl; // Custom endpoint for emulators or non-standard GCS endpoints
    private String apiKey;
    private boolean useDefaultCredentialsChain;

    public GoogleCloudStorageBlobStoreInfo() {
        super();
    }

    public GoogleCloudStorageBlobStoreInfo(String id) {
        super(id);
    }

    @Override
    public GoogleCloudStorageBlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {
        GeoWebCacheEnvironment environment = GeoWebCacheExtensions.bean(GeoWebCacheEnvironment.class);
        return createInstance(layers, environment);
    }

    GoogleCloudStorageBlobStore createInstance(TileLayerDispatcher layers, GeoWebCacheEnvironment environment)
            throws StorageException {

        GoogleCloudStorageClient client =
                GoogleCloudStorageClient.builder(this, environment).build();
        return new GoogleCloudStorageBlobStore(client, layers);
    }

    @Override
    public String getLocation() {
        if (StringUtils.hasText(prefix)) {
            return "gs://" + bucket + "/" + prefix;
        }
        return "gs://" + bucket;
    }

    /** @return The Google Cloud Storage project ID. */
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * @return The ID of the project to bill for quota purposes.
     * @see <a href="https://cloud.google.com/storage/docs/requester-pays#project-iam">Requester Pays</a>
     */
    public String getQuotaProjectId() {
        return quotaProjectId;
    }

    public void setQuotaProjectId(String quotaProjectId) {
        this.quotaProjectId = quotaProjectId;
    }

    /** @return The name of the GCS bucket. */
    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    /** @return The blob name prefix within the bucket, or {@code null} if the cache operates at the bucket's root. */
    public String getPrefix() {
        return prefix;
    }

    /** @param prefix the blob name prefix applied to the cache, if not set, the cache operates at the bucket's root */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /** @return An alternative GCS endpoint URL, for use with emulators or GCS-compatible services. */
    public String getEndpointUrl() {
        return endpointUrl;
    }

    /**
     * Set an alternative GCS endpoint URL, for example, for compatible services
     *
     * @param endpoint
     */
    public void setEndpointUrl(String endpoint) {
        this.endpointUrl = endpoint;
    }

    /**
     * @return The API key for authentication. If provided, it is used in preference to the default credentials chain.
     */
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * @return {@code true} if the default Google Cloud credentials chain should be used for authentication.
     * @see com.google.auth.oauth2.GoogleCredentials#getApplicationDefault()
     */
    public boolean getUseDefaultCredentialsChain() {
        return useDefaultCredentialsChain;
    }

    /**
     * Sets whether to use the default Google Cloud credentials chain.
     *
     * @param useDefaultCredentialsChain {@code true} to enable, {@code false} to disable.
     */
    public void setUseDefaultCredentialsChain(boolean useDefaultCredentialsChain) {
        this.useDefaultCredentialsChain = useDefaultCredentialsChain;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (super.equals(o)) {
            GoogleCloudStorageBlobStoreInfo other = (GoogleCloudStorageBlobStoreInfo) o;
            return Objects.equals(projectId, other.projectId)
                    && Objects.equals(bucket, other.bucket)
                    && Objects.equals(prefix, other.prefix)
                    && Objects.equals(endpointUrl, other.endpointUrl)
                    && Objects.equals(apiKey, other.apiKey)
                    && Objects.equals(useDefaultCredentialsChain, other.useDefaultCredentialsChain)
                    && Objects.equals(quotaProjectId, other.quotaProjectId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode()
                + Objects.hash(
                        projectId, bucket, prefix, endpointUrl, apiKey, useDefaultCredentialsChain, quotaProjectId);
    }
}
