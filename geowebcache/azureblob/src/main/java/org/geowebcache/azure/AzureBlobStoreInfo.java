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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.net.InetSocketAddress;
import java.net.Proxy;
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

/** Plain old java object representing the configuration for an S3 blob store. */
public class AzureBlobStoreInfo extends BlobStoreInfo {

    /**
     * Max number of connections used inside the Netty HTTP client. Might seem a lot, but when
     * deleting we have to issue a delete on each single tile, so we need a large parallelism to
     * make that feasible
     */
    public static final int DEFAULT_CONNECTIONS = 100;

    static Log log = LogFactory.getLog(AzureBlobStoreInfo.class);

    private String container;

    private String prefix;

    private String accountName;

    private String accountKey;

    private Integer maxConnections;

    private Boolean useHTTPS = true;

    private String proxyHost;

    private Integer proxyPort;

    private String proxyUsername;

    private String proxyPassword;

    private String serviceURL;

    public AzureBlobStoreInfo() {
        super();
    }

    public AzureBlobStoreInfo(String id) {
        super(id);
    }

    /** @return the name of the AWS S3 bucket where to store tiles */
    public String getContainer() {
        return container;
    }

    /** Sets the name of the AWS S3 bucket where to store tiles */
    public void setContainer(String container) {
        this.container = container;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

    public String getServiceURL() {
        return serviceURL;
    }

    public void setServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
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
        return maxConnections == null ? DEFAULT_CONNECTIONS : maxConnections;
    }

    /** Sets the maximum number of allowed open HTTP connections. */
    public void setMaxConnections(Integer maxConnections) {
        this.maxConnections = maxConnections;
    }

    /** @return whether to use HTTPS (true) or HTTP (false) when talking to S3 (defaults to true) */
    public Boolean isUseHTTPS() {
        return useHTTPS;
    }

    /** @param useHTTPS whether to use HTTPS (true) or HTTP (false) when talking to S3 */
    public void setUseHTTPS(Boolean useHTTPS) {
        this.useHTTPS = useHTTPS;
    }

    /**
     * Returns the optional proxy host the client will connect through.
     *
     * @return The proxy host the client will connect through.
     */
    @Nullable
    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * Sets the optional proxy host the client will connect through.
     *
     * @param proxyHost The proxy host the client will connect through.
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * Returns the optional proxy port the client will connect through.
     *
     * @return The proxy port the client will connect through.
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * Sets the optional proxy port the client will connect through.
     *
     * @param proxyPort The proxy port the client will connect through.
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Returns the optional proxy user name to use if connecting through a proxy.
     *
     * @return The optional proxy user name the configured client will use if connecting through a
     *     proxy.
     */
    @Nullable
    public String getProxyUsername() {
        return proxyUsername;
    }

    /**
     * Sets the optional proxy user name to use if connecting through a proxy.
     *
     * @param proxyUsername The proxy user name to use if connecting through a proxy.
     */
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    /**
     * Returns the optional proxy password to use when connecting through a proxy.
     *
     * @return The password to use when connecting through a proxy.
     */
    @Nullable
    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * Sets the optional proxy password to use when connecting through a proxy.
     *
     * @param proxyPassword The password to use when connecting through a proxy.
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
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

    @Override
    public BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {

        checkNotNull(layers);
        checkState(getName() != null);
        checkState(
                isEnabled(),
                "Can't call S3BlobStoreConfig.createInstance() is blob store is not enabled");
        return new AzureBlobStore(this, layers, lockProvider);
    }

    @Override
    public String getLocation() {
        String bucket = this.getContainer();
        String prefix = this.getPrefix();
        if (prefix == null) {
            return String.format("container: %s", bucket);
        } else {
            return String.format("container: %s prefix: %s", bucket, prefix);
        }
    }

    Proxy getProxy() {
        if (proxyHost != null) {
            return new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(proxyHost, proxyPort != null ? proxyPort : 8888));
        }
        return null;
    }
}
