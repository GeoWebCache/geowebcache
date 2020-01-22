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
 * @author Dana Lambert, Catalyst IT Ltd NZ, Copyright 2020
 */
package org.geowebcache.swift;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Properties;
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;
import org.jclouds.ContextBuilder;
import org.jclouds.openstack.keystone.config.KeystoneProperties;
import org.jclouds.openstack.swift.v1.SwiftApi;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedBlobStoreContext;
import org.jclouds.openstack.swift.v1.features.ContainerApi;

/** Plain old java object representing the configuration for an Swift blob store. */
@SuppressWarnings("deprecation")
public class SwiftBlobStoreInfo extends BlobStoreInfo {

    static Log log = LogFactory.getLog(SwiftBlobStoreInfo.class);

    private static final long serialVersionUID = 9072751143836460389L;

    private String bucket;

    private String prefix;

    private Integer maxConnections;

    private Boolean useHTTPS = true;

    private String jcloudsProvider;

    private String jcloudsRegion;

    private String jcloudsKeystoneVersion;

    private String jcloudsKeystoneScope;

    private String jcloudsKeystoneDomainName;

    private String jcloudsIdentity;

    private String jcloudsCredential;

    private Boolean useGzip;

    private String endpoint;

    public SwiftBlobStoreInfo() {
        super();
    }

    public SwiftBlobStoreInfo(String id) {
        super(id);
    }

    /** @return the name of the Swift bucket where to store tiles */
    public String getBucket() {
        return bucket;
    }

    /** Sets the name of the Swift bucket where to store tiles */
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    /** @return the Swift endpoint */
    public String getEndpoint() {
        return endpoint;
    }

    /** Sets the Swift endpoint */
    public void setEndpoint(String host) {
        this.endpoint = host;
    }

    @Override
    public BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {

        checkNotNull(layers);
        checkState(getName() != null);
        checkState(
                isEnabled(),
                "Can't call SwiftBlobStoreConfig.createInstance() if blob store is not enabled");
        return new SwiftBlobStore(this, layers);
    }

    /**
     * Returns the base prefix, which is a prefix path to use as the root to store tiles under the
     * bucket.
     *
     * @return optional string for a "base prefix"
     */
    @Nullable
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /** @return The maximum number of allowed open HTTP connections. */
    public Integer getMaxConnections() {
        return maxConnections;
    }

    /** Sets the maximum number of allowed open HTTP connections. */
    public void setMaxConnections(Integer maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * @return whether to use HTTPS (true) or HTTP (false) when talking to Swift (defaults to true)
     */
    public Boolean isUseHTTPS() {
        return useHTTPS;
    }

    /** @param useHTTPS whether to use HTTPS (true) or HTTP (false) when talking to Swift */
    public void setUseHTTPS(Boolean useHTTPS) {
        this.useHTTPS = useHTTPS;
    }

    /**
     * Checks if gzip compression is used
     *
     * @return if gzip compression is used
     */
    public Boolean isUseGzip() {
        return useGzip;
    }

    /**
     * Sets whether gzip compression should be used
     *
     * @param use whether gzip compression should be used
     */
    public void setUseGzip(Boolean use) {
        this.useGzip = use;
    }

    public String getJcloudsProvider() {
        return jcloudsProvider;
    }

    public void setJcloudsProvider(String jcloudsProvider) {
        this.jcloudsProvider = jcloudsProvider;
    }

    public String getJcloudsRegion() {
        return jcloudsRegion;
    }

    public void setJcloudsRegion(String jcloudsRegion) {
        this.jcloudsRegion = jcloudsRegion;
    }

    public String getJcloudsKeystoneVersion() {
        return jcloudsKeystoneVersion;
    }

    public void setJcloudsKeystoneVersion(String jcloudsKeystoneVersion) {
        this.jcloudsKeystoneVersion = jcloudsKeystoneVersion;
    }

    public String getJcloudsKeystoneScope() {
        return jcloudsKeystoneScope;
    }

    public void setJcloudsKeystoneScope(String jcloudsKeystoneScope) {
        this.jcloudsKeystoneScope = jcloudsKeystoneScope;
    }

    public String getJcloudsKeystoneDomainName() {
        return jcloudsKeystoneDomainName;
    }

    public void setJcloudsKeystoneDomainName(String jcloudsKeystoneDomainName) {
        this.jcloudsKeystoneDomainName = jcloudsKeystoneDomainName;
    }

    public String getJcloudsIdentity() {
        return jcloudsIdentity;
    }

    public void setJcloudsIdentity(String jcloudsIdentity) {
        this.jcloudsIdentity = jcloudsIdentity;
    }

    public String getJcloudsCredential() {
        return jcloudsCredential;
    }

    public void setJcloudsCredential(String jcloudsCredential) {
        this.jcloudsCredential = jcloudsCredential;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    /** @return {@link SwiftApi} constructed from this {@link SwiftBlobStoreInfo}. */
    public SwiftApi buildApi() {

        final Properties overrides = new Properties();
        overrides.put(KeystoneProperties.KEYSTONE_VERSION, jcloudsKeystoneVersion);
        overrides.put(KeystoneProperties.SCOPE, jcloudsKeystoneScope);
        overrides.put(KeystoneProperties.PROJECT_DOMAIN_NAME, jcloudsKeystoneDomainName);

        String provider = jcloudsProvider;
        String identity = jcloudsIdentity;
        String credential = jcloudsCredential;

        SwiftApi context =
                ContextBuilder.newBuilder(provider)
                        .endpoint(endpoint)
                        .credentials(identity, credential)
                        .overrides(overrides)
                        .buildApi(SwiftApi.class);

        ContainerApi containerApi = context.getContainerApi(jcloudsRegion);

        // Creates bucket if it doesn't already exist
        if (containerApi.get(bucket) == null) {
            containerApi.create(bucket);
        }

        return context;
    }

    /** @return {@link SwiftApi} constructed from this {@link SwiftBlobStoreInfo}. */
    public RegionScopedBlobStoreContext getBlobStore() {

        final Properties overrides = new Properties();
        overrides.put(KeystoneProperties.KEYSTONE_VERSION, jcloudsKeystoneVersion);
        overrides.put(KeystoneProperties.SCOPE, jcloudsKeystoneScope);
        overrides.put(KeystoneProperties.PROJECT_DOMAIN_NAME, jcloudsKeystoneDomainName);

        String provider = jcloudsProvider;
        String identity = jcloudsIdentity;
        String credential = jcloudsCredential;

        ContextBuilder builder =
                ContextBuilder.newBuilder(provider)
                        .endpoint(endpoint)
                        .credentials(identity, credential)
                        .overrides(overrides);

        return builder.build(RegionScopedBlobStoreContext.class);
    }

    @Override
    public String getLocation() {
        String bucket = this.getBucket();
        String prefix = this.getPrefix();
        if (prefix == null) {
            return String.format("bucket: %s", bucket);
        } else {
            return String.format("bucket: %s prefix: %s", bucket, prefix);
        }
    }
}
