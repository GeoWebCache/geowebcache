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

/**
 * Java object representing the configuration for an Swift blob store. Contains methods for building
 * instance of JClouds API and Blobstore.
 */
@SuppressWarnings("deprecation")
public class SwiftBlobStoreInfo extends BlobStoreInfo {

    static Log log = LogFactory.getLog(SwiftBlobStoreInfo.class);

    private static final long serialVersionUID = 9072751143836460389L;

    private String container;

    private String prefix;

    private String provider;

    private String region;

    private String keystoneVersion;

    private String keystoneScope;

    private String keystoneDomainName;

    private String identity;

    private String password;

    private String endpoint;

    public SwiftBlobStoreInfo() {
        super();
    }

    public SwiftBlobStoreInfo(String id) {
        super(id);
    }

    /** @return the name of the Swift bucket where to store tiles */
    public String getContainer() {
        return container;
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

    public String getRegion() {
        return region;
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
        overrides.put(KeystoneProperties.KEYSTONE_VERSION, keystoneVersion);
        overrides.put(KeystoneProperties.SCOPE, keystoneScope);
        overrides.put(KeystoneProperties.PROJECT_DOMAIN_NAME, keystoneDomainName);

        String provider = this.provider;
        String identity = this.identity;
        String credential = this.password;

        SwiftApi context =
                ContextBuilder.newBuilder(provider)
                        .endpoint(endpoint)
                        .credentials(identity, credential)
                        .overrides(overrides)
                        .buildApi(SwiftApi.class);

        ContainerApi containerApi = context.getContainerApi(region);

        // Creates bucket if it doesn't already exist
        if (containerApi.get(container) == null) {
            containerApi.create(container);
        }

        return context;
    }

    /** @return {@link SwiftApi} constructed from this {@link SwiftBlobStoreInfo}. */
    public RegionScopedBlobStoreContext getBlobStore() {

        final Properties overrides = new Properties();
        overrides.put(KeystoneProperties.KEYSTONE_VERSION, keystoneVersion);
        overrides.put(KeystoneProperties.SCOPE, keystoneScope);
        overrides.put(KeystoneProperties.PROJECT_DOMAIN_NAME, keystoneDomainName);

        String provider = this.provider;
        String identity = this.identity;
        String credential = this.password;

        ContextBuilder builder =
                ContextBuilder.newBuilder(provider)
                        .endpoint(endpoint)
                        .credentials(identity, credential)
                        .overrides(overrides);

        return builder.build(RegionScopedBlobStoreContext.class);
    }

    @Override
    public String getLocation() {
        String bucket = this.getContainer();
        String prefix = this.getPrefix();
        if (prefix == null) {
            return String.format("bucket: %s", bucket);
        } else {
            return String.format("bucket: %s prefix: %s", bucket, prefix);
        }
    }
}
