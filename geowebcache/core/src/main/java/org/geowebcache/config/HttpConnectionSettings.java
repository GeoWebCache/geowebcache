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
 * @author Lennart Juette, PTV AG (http://www.ptvag.com) 2010
 */
package org.geowebcache.config;

import java.util.Objects;

/**
 * Configuration class for global HTTP connection settings used by all WMS layers. This replaces the per-layer
 * concurrency parameter which was ineffective due to the static singleton connection manager.
 */
public class HttpConnectionSettings {

    /** Default maximum total connections */
    public static final int DEFAULT_MAX_CONNECTIONS_TOTAL = 20;

    /** Default maximum connections per route */
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 2;

    /** Default connection timeout in seconds */
    public static final int DEFAULT_CONNECTION_TIMEOUT = 30;

    /** Default socket timeout in seconds */
    public static final int DEFAULT_SOCKET_TIMEOUT = 60;

    private Integer maxConnectionsTotal;
    private Integer maxConnectionsPerRoute;
    private Integer connectionTimeout;
    private Integer socketTimeout;

    public HttpConnectionSettings() {
        // Default constructor for XStream
    }

    public HttpConnectionSettings(
            Integer maxConnectionsTotal,
            Integer maxConnectionsPerRoute,
            Integer connectionTimeout,
            Integer socketTimeout) {
        this.maxConnectionsTotal = maxConnectionsTotal;
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        this.connectionTimeout = connectionTimeout;
        this.socketTimeout = socketTimeout;
    }

    /** @return the maximum total number of connections in the connection pool */
    public int getMaxConnectionsTotal() {
        return maxConnectionsTotal != null ? maxConnectionsTotal : DEFAULT_MAX_CONNECTIONS_TOTAL;
    }

    /** @param maxConnectionsTotal the maximum total number of connections to set */
    public void setMaxConnectionsTotal(Integer maxConnectionsTotal) {
        this.maxConnectionsTotal = maxConnectionsTotal;
    }

    /** @return the maximum number of connections per route (per backend server) */
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute != null ? maxConnectionsPerRoute : DEFAULT_MAX_CONNECTIONS_PER_ROUTE;
    }

    /** @param maxConnectionsPerRoute the maximum connections per route to set */
    public void setMaxConnectionsPerRoute(Integer maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }

    /** @return the connection timeout in seconds */
    public int getConnectionTimeout() {
        return connectionTimeout != null ? connectionTimeout : DEFAULT_CONNECTION_TIMEOUT;
    }

    /** @param connectionTimeout the connection timeout to set */
    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /** @return the socket timeout in seconds */
    public int getSocketTimeout() {
        return socketTimeout != null ? socketTimeout : DEFAULT_SOCKET_TIMEOUT;
    }

    /** @param socketTimeout the socket timeout to set */
    public void setSocketTimeout(Integer socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HttpConnectionSettings that = (HttpConnectionSettings) obj;
        return Objects.equals(maxConnectionsTotal, that.maxConnectionsTotal)
                && Objects.equals(maxConnectionsPerRoute, that.maxConnectionsPerRoute)
                && Objects.equals(connectionTimeout, that.connectionTimeout)
                && Objects.equals(socketTimeout, that.socketTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxConnectionsTotal, maxConnectionsPerRoute, connectionTimeout, socketTimeout);
    }

    @Override
    public String toString() {
        return "HttpConnectionSettings{" + "maxConnectionsTotal="
                + getMaxConnectionsTotal() + ", maxConnectionsPerRoute="
                + getMaxConnectionsPerRoute() + ", connectionTimeout="
                + getConnectionTimeout() + ", socketTimeout="
                + getSocketTimeout() + '}';
    }
}
