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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.Serial;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;

/** Plain old java object representing the configuration for an Azure blob store. */
public class AzureBlobStoreInfo extends BlobStoreInfo {
    @Serial
    private static final long serialVersionUID = -8068069256598987874L;

    /**
     * Max number of connections used inside the Netty HTTP client. Might seem a lot, but when deleting we have to issue
     * a delete on each single tile, so we need a large parallelism to make that feasible
     */
    public static final int DEFAULT_CONNECTIONS = 100;

    static Logger log = Logging.getLogger(AzureBlobStoreInfo.class.getName());

    private String container;

    private String prefix;

    private String accountName;

    private String accountKey;

    private String maxConnections;

    private boolean useHTTPS = true;

    private String proxyHost;

    private String proxyPort;

    private String proxyUsername;

    private String proxyPassword;

    private String serviceURL;

    public AzureBlobStoreInfo() {
        super();
    }

    public AzureBlobStoreInfo(String id) {
        super(id);
    }

    /** @return the name of the Azure container where to store tiles */
    public String getContainer() {
        return container;
    }

    /** Sets the name of the Azure container where to store tiles */
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
     * Returns the base prefix, which is a prefix path to use as the root to store tiles under the container.
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
    public String getMaxConnections() {
        return maxConnections == null ? String.valueOf(DEFAULT_CONNECTIONS) : maxConnections;
    }

    /** Sets the maximum number of allowed open HTTP connections. */
    public void setMaxConnections(String maxConnections) {
        this.maxConnections = maxConnections;
    }

    /** @return whether to use HTTPS (true) or HTTP (false) when talking to Azure (defaults to true) */
    public Boolean isUseHTTPS() {
        return useHTTPS;
    }

    /** @param useHTTPS whether to use HTTPS (true) or HTTP (false) when talking to Azure */
    public void setUseHTTPS(boolean useHTTPS) {
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
    public String getProxyPort() {
        return proxyPort;
    }

    /**
     * Sets the optional proxy port the client will connect through.
     *
     * @param proxyPort The proxy port the client will connect through.
     */
    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Returns the optional proxy user name to use if connecting through a proxy.
     *
     * @return The optional proxy user name the configured client will use if connecting through a proxy.
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
    public BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider) throws StorageException {
        checkNotNull(layers);
        checkState(getName() != null);
        checkState(isEnabled(), "Can't call AzureBlobStoreConfig.createInstance() is blob store is not enabled");
        if (log.isLoggable(Level.FINE)) log.fine("Creating Azure Blob Store instance [name=" + getName() + "]");
        final AzureBlobStoreData storeData =
                new AzureBlobStoreData(this, GeoWebCacheExtensions.bean(GeoWebCacheEnvironment.class));
        return new AzureBlobStore(storeData, layers, lockProvider);
    }

    @Override
    public String getLocation() {
        String container = this.getContainer();
        String prefix = this.getPrefix();
        if (prefix == null) {
            return "container: %s".formatted(container);
        } else {
            return "container: %s prefix: %s".formatted(container, prefix);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((accountKey == null) ? 0 : accountKey.hashCode());
        result = prime * result + ((accountName == null) ? 0 : accountName.hashCode());
        result = prime * result + ((container == null) ? 0 : container.hashCode());
        result = prime * result + ((maxConnections == null) ? 0 : maxConnections.hashCode());
        result = prime * result + ((prefix == null) ? 0 : prefix.hashCode());
        result = prime * result + ((proxyHost == null) ? 0 : proxyHost.hashCode());
        result = prime * result + ((proxyPassword == null) ? 0 : proxyPassword.hashCode());
        result = prime * result + ((proxyPort == null) ? 0 : proxyPort.hashCode());
        result = prime * result + ((proxyUsername == null) ? 0 : proxyUsername.hashCode());
        result = prime * result + ((serviceURL == null) ? 0 : serviceURL.hashCode());
        result = prime * result + (useHTTPS ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        AzureBlobStoreInfo other = (AzureBlobStoreInfo) obj;
        if (accountKey == null) {
            if (other.accountKey != null) return false;
        } else if (!accountKey.equals(other.accountKey)) return false;
        if (accountName == null) {
            if (other.accountName != null) return false;
        } else if (!accountName.equals(other.accountName)) return false;
        if (container == null) {
            if (other.container != null) return false;
        } else if (!container.equals(other.container)) return false;
        if (maxConnections == null) {
            if (other.maxConnections != null) return false;
        } else if (!maxConnections.equals(other.maxConnections)) return false;
        if (prefix == null) {
            if (other.prefix != null) return false;
        } else if (!prefix.equals(other.prefix)) return false;
        if (proxyHost == null) {
            if (other.proxyHost != null) return false;
        } else if (!proxyHost.equals(other.proxyHost)) return false;
        if (proxyPassword == null) {
            if (other.proxyPassword != null) return false;
        } else if (!proxyPassword.equals(other.proxyPassword)) return false;
        if (proxyPort == null) {
            if (other.proxyPort != null) return false;
        } else if (!proxyPort.equals(other.proxyPort)) return false;
        if (proxyUsername == null) {
            if (other.proxyUsername != null) return false;
        } else if (!proxyUsername.equals(other.proxyUsername)) return false;
        if (serviceURL == null) {
            if (other.serviceURL != null) return false;
        } else if (!serviceURL.equals(other.serviceURL)) return false;
        if (useHTTPS != other.useHTTPS) return false;
        return true;
    }

    @Override
    public String toString() {
        return "AzureBlobStoreInfo [container="
                + container
                + ", prefix="
                + prefix
                + ", accountName="
                + accountName
                + ", accountKey="
                + accountKey
                + ", maxConnections="
                + maxConnections
                + ", useHTTPS="
                + useHTTPS
                + ", proxyHost="
                + proxyHost
                + ", proxyPort="
                + proxyPort
                + ", proxyUsername="
                + proxyUsername
                + ", proxyPassword="
                + proxyPassword
                + ", serviceURL="
                + serviceURL
                + ", getName()="
                + getName()
                + ", getId()="
                + getId()
                + ", isEnabled()="
                + isEnabled()
                + ", isDefault()="
                + isDefault()
                + "]";
    }
}
