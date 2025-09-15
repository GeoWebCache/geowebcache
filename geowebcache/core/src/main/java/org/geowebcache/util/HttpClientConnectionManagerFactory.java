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
package org.geowebcache.util;

import java.util.logging.Logger;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.HttpConnectionSettings;

/**
 * Factory class for creating and managing HTTP connection managers. This replaces the static singleton approach with a
 * configurable solution that properly respects the connection settings.
 */
public class HttpClientConnectionManagerFactory {

    static final Logger log = Logging.getLogger(HttpClientConnectionManagerFactory.class.toString());

    private static volatile HttpClientConnectionManagerFactory instance;
    private volatile HttpClientConnectionManager connectionManager;
    private volatile HttpConnectionSettings settings;

    private HttpClientConnectionManagerFactory() {
        // Private constructor for singleton
    }

    /** Get the singleton instance of the factory */
    public static HttpClientConnectionManagerFactory getInstance() {
        if (instance == null) {
            synchronized (HttpClientConnectionManagerFactory.class) {
                if (instance == null) {
                    instance = new HttpClientConnectionManagerFactory();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the connection manager with the given settings. This method should be called once during application
     * startup.
     *
     * @param settings the HTTP connection settings to use
     */
    public synchronized void initialize(HttpConnectionSettings settings) {
        if (this.settings != null && this.settings.equals(settings)) {
            // Settings haven't changed, no need to recreate
            return;
        }

        this.settings = settings != null ? settings : new HttpConnectionSettings();

        // Close existing connection manager if it exists
        if (connectionManager != null) {
            try {
                connectionManager.shutdown();
            } catch (Exception e) {
                log.warning("Error closing existing connection manager: " + e.getMessage());
            }
        }

        // Create new connection manager with proper settings
        PoolingHttpClientConnectionManager newManager = new PoolingHttpClientConnectionManager();
        newManager.setMaxTotal(this.settings.getMaxConnectionsTotal());
        newManager.setDefaultMaxPerRoute(this.settings.getMaxConnectionsPerRoute());

        this.connectionManager = newManager;

        log.info("Initialized HTTP connection manager with settings: " + this.settings);
    }

    /**
     * Get the current connection manager. If not initialized, creates one with default settings.
     *
     * @return the HTTP connection manager
     */
    public HttpClientConnectionManager getConnectionManager() {
        if (connectionManager == null) {
            synchronized (this) {
                if (connectionManager == null) {
                    // Initialize with default settings if not already done
                    initialize(new HttpConnectionSettings());
                }
            }
        }
        return connectionManager;
    }

    /**
     * Get the current connection settings
     *
     * @return the current HTTP connection settings
     */
    public HttpConnectionSettings getSettings() {
        return settings != null ? settings : new HttpConnectionSettings();
    }

    /** Shutdown the connection manager and release resources. This should be called during application shutdown. */
    public synchronized void shutdown() {
        if (connectionManager != null) {
            try {
                connectionManager.shutdown();
                connectionManager = null;
                settings = null;
                log.info("HTTP connection manager shutdown completed");
            } catch (Exception e) {
                log.warning("Error during connection manager shutdown: " + e.getMessage());
            }
        }
    }
}
