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
 * @author Fernando Mino, GeoSolutions, Copyright 2019
 */
package org.geowebcache.azure;

import java.net.InetSocketAddress;
import java.net.Proxy;
import org.geowebcache.GeoWebCacheEnvironment;

/** Azure Blobstore type-resolved data from a {@link AzureBlobStoreInfo} using enviroment variables if enabled. */
public class AzureBlobStoreData {

    private String container;
    private String prefix;
    private String accountName;
    private String accountKey;
    private Integer maxConnections;
    private boolean useHTTPS;
    private String proxyHost;
    private Integer proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    private String serviceURL;

    public AzureBlobStoreData() {}

    public AzureBlobStoreData(final AzureBlobStoreInfo storeInfo, final GeoWebCacheEnvironment environment) {
        environment
                .resolveValueIfEnabled(storeInfo.getContainer(), String.class)
                .ifPresent(x -> this.container = x);
        environment.resolveValueIfEnabled(storeInfo.getPrefix(), String.class).ifPresent(x -> this.prefix = x);
        environment
                .resolveValueIfEnabled(storeInfo.getAccountName(), String.class)
                .ifPresent(x -> this.accountName = x);
        environment
                .resolveValueIfEnabled(storeInfo.getAccountKey(), String.class)
                .ifPresent(x -> this.accountKey = x);
        environment
                .resolveValueIfEnabled(storeInfo.getMaxConnections(), Integer.class)
                .ifPresent(x -> this.maxConnections = x);
        this.useHTTPS = storeInfo.isUseHTTPS();
        environment
                .resolveValueIfEnabled(storeInfo.getProxyHost(), String.class)
                .ifPresent(x -> this.proxyHost = x);
        environment
                .resolveValueIfEnabled(storeInfo.getProxyPort(), Integer.class)
                .ifPresent(x -> this.proxyPort = x);
        environment
                .resolveValueIfEnabled(storeInfo.getProxyUsername(), String.class)
                .ifPresent(x -> this.proxyUsername = x);
        environment
                .resolveValueIfEnabled(storeInfo.getProxyPassword(), String.class)
                .ifPresent(x -> this.proxyPassword = x);
        environment
                .resolveValueIfEnabled(storeInfo.getServiceURL(), String.class)
                .ifPresent(x -> this.serviceURL = x);
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
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

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(Integer maxConnections) {
        this.maxConnections = maxConnections;
    }

    public boolean isUseHTTPS() {
        return useHTTPS;
    }

    public void setUseHTTPS(boolean useHTTPS) {
        this.useHTTPS = useHTTPS;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    /** unused */
    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    /** unused */
    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public String getServiceURL() {
        return serviceURL;
    }

    public void setServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
    }

    public String getLocation() {
        String container = this.getContainer();
        String prefix = this.getPrefix();
        if (prefix == null) {
            return "container: %s".formatted(container);
        } else {
            return "container: %s prefix: %s".formatted(container, prefix);
        }
    }

    Proxy getProxy() {
        if (proxyHost != null) {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort != null ? proxyPort : 8888));
        }
        return null;
    }
}
