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

/**
 * Configuration for XStream persistence and {@link #createInstance(TileLayerDispatcher, LockProvider) factory} object
 * for {@link GoogleCloudStorageBlobStore}
 */
@SuppressWarnings("serial")
public class GoogleCloudStorageBlobStoreInfo extends BlobStoreInfo {

    private String projectId;
    private String quotaProjectId;
    private String bucket;
    private String prefix;
    private String host; // Custom endpoint for emulators or non-standard GCS endpoints
    private String apiKey;
    private boolean defaultCredentialsChain;

    @Override
    public GoogleCloudStorageBlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {
        GeoWebCacheEnvironment environment = GeoWebCacheExtensions.bean(GeoWebCacheEnvironment.class);
        return createInstance(layers, environment);
    }

    GoogleCloudStorageBlobStore createInstance(TileLayerDispatcher layers, GeoWebCacheEnvironment environment)
            throws StorageException {

        GoogleCloudStorageClient storage = GoogleCloudStorageClient.buildClient(this, environment);
        return new GoogleCloudStorageBlobStore(storage, layers);
    }

    @Override
    public String getLocation() {
        return "gs://" + bucket;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getQuotaProjectId() {
        return quotaProjectId;
    }

    public void setQuotaProjectId(String quotaProjectId) {
        this.quotaProjectId = quotaProjectId;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isDefaultCredentialsChain() {
        return defaultCredentialsChain;
    }

    public void setDefaultCredentialsChain(boolean defaultCredentialsChain) {
        this.defaultCredentialsChain = defaultCredentialsChain;
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
                    && Objects.equals(host, other.host)
                    && Objects.equals(apiKey, other.apiKey)
                    && Objects.equals(defaultCredentialsChain, other.defaultCredentialsChain)
                    && Objects.equals(quotaProjectId, other.quotaProjectId);
        }
        return false;
    }
}
