package org.geowebcache.config;

import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.locks.LockProvider;

import java.io.IOException;

public interface ServerConfiguration extends BaseConfiguration {

    /**
     * Service information such as you or your company's details that you want provided in capabilities documents.
     * @return ServiceInformation for the ServerConfiguration
     */
    ServiceInformation getServiceInformation();

    /**
     * Used to set the service information for this configuration.
     * @param serviceInfo
     */
    void setServiceInformation(ServiceInformation serviceInfo) throws IOException;

    /**
     * Runtime statistics run, by default, every three second and provide data about how many requests the system has
     * been serving in the past 3, 15 and 60 seconds, as well as aggregate numbers.
     * @return True or False if disabled
     */
    boolean isRuntimeStatsEnabled();

    /**
     * Set if runtime statistics is enabled for this configuration, True or False.
     * @param isEnabled
     */
    void setIsRuntimeStatsEnabled(boolean isEnabled) throws IOException;

    /**
     * The name of the lock provider.
     * @return The the LockProvider chosen for the Server Configuration
     */
    LockProvider getLockProvider();

    /**
     * Set the lock provider for this configuration.
     * @param lockProvider
     */
    void setLockProvider(LockProvider lockProvider) throws IOException;

    /**
     * Used for getting the "fullWMS" parameter from GeoWebCacheConfigration if present
     * @return True, False or Null if not present
     */
    Boolean getfullWMS();

    /**
     * Used to set fullWMS is parameter for this configuration if present.
     * @param isFullWMS
     */
    void setFullWMS(boolean isFullWMS) throws IOException;

    /**
     * If this method returns NULL CITE strict compliance mode should not be considered for WMTS
     * service implementation.
     *
     * @return may return TRUE, FALSE or NULL
     */
    boolean isWmtsCiteCompliant();

    /**
     * Can be used to force WMTS service implementation to be strictly compliant with the
     * correspondent CITE tests.
     *
     * @param wmtsCiteStrictCompliant TRUE or FALSE, activating or deactivation CITE
     *                                strict compliance mode for WMTS
     */
    void setWmtsCiteStrictCompliant(boolean wmtsCiteStrictCompliant) throws IOException;

    /**
     * The backend timeout is the number of seconds GWC will wait for a backend server to return something
     * before closing the connection.
     * @return The backend timeout for this configuration.
     */
    Integer getBackendTimeout();

    /**
     * @param backendTimeout
     * @throws IOException
     */

    void setBackendTimeout(Integer backendTimeout) throws IOException;

    /**
     *  Determines whether cached=false is allowed for requests going through the WMS service,
     *  including converters such as Google Maps.
     * @return True or False
     */
    Boolean getCacheBypassAllowed();

    /**
     *
     * @param cacheBypassAllowed
     * @throws IOException
     */
    void setCacheBypassAllowed(Boolean cacheBypassAllowed) throws IOException;

    /**
     * The version number should match the XSD namespace and the version of GWC
     * @return
     */
    String getVersion();

}
