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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import java.io.IOException;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.locks.LockProvider;

/**
 * Interface for managing global server configuration information.
 *
 * <p>In general, a running GeoWebCache application should only include a single instance of ServerConfiguration
 */
public interface ServerConfiguration extends BaseConfiguration {

    /**
     * Service information such as you or your company's details that you want provided in capabilities documents.
     *
     * @return ServiceInformation for the ServerConfiguration
     */
    ServiceInformation getServiceInformation();

    /** Used to set the service information for this configuration. */
    void setServiceInformation(ServiceInformation serviceInfo) throws IOException;

    /**
     * Runtime statistics run, by default, every three second and provide data about how many requests the system has
     * been serving in the past 3, 15 and 60 seconds, as well as aggregate numbers.
     *
     * @return True or False if disabled
     */
    Boolean isRuntimeStatsEnabled();

    /** Set if runtime statistics is enabled for this configuration, True or False. */
    void setRuntimeStatsEnabled(Boolean isEnabled) throws IOException;

    /**
     * The name of the lock provider.
     *
     * @return The the LockProvider chosen for the Server Configuration
     */
    LockProvider getLockProvider();

    /** Set the lock provider for this configuration. */
    void setLockProvider(LockProvider lockProvider) throws IOException;

    /**
     * Used for getting the "fullWMS" parameter from GeoWebCacheConfigration if present
     *
     * @return True, False or Null if not present
     */
    Boolean isFullWMS();

    /** Used to set fullWMS is parameter for this configuration if present. */
    void setFullWMS(Boolean isFullWMS) throws IOException;

    /**
     * If this method returns NULL CITE strict compliance mode should not be considered for WMTS service implementation.
     *
     * @return may return TRUE, FALSE or NULL
     */
    Boolean isWmtsCiteCompliant();

    /**
     * Can be used to force WMTS service implementation to be strictly compliant with the correspondent CITE tests.
     *
     * @param wmtsCiteStrictCompliant TRUE or FALSE, activating or deactivation CITE strict compliance mode for WMTS
     */
    void setWmtsCiteCompliant(Boolean wmtsCiteStrictCompliant) throws IOException;

    /**
     * The backend timeout is the number of seconds GWC will wait for a backend server to return something before
     * closing the connection.
     *
     * @return The backend timeout for this configuration.
     */
    Integer getBackendTimeout();

    /** */
    void setBackendTimeout(Integer backendTimeout) throws IOException;

    /**
     * Determines whether cached=false is allowed for requests going through the WMS service, including converters such
     * as Google Maps.
     *
     * @return True or False
     */
    Boolean isCacheBypassAllowed();

    /** */
    void setCacheBypassAllowed(Boolean cacheBypassAllowed) throws IOException;

    /** The version number should match the XSD namespace and the version of GWC */
    String getVersion();
}
