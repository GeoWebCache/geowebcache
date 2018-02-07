/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */

package org.geowebcache.config;

import java.util.ArrayList;
import java.util.List;

import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.MemoryLockProvider;
import org.geowebcache.mime.FormatModifier;

/**
 * POJO for geowebcache.xml configuration
 * 
 * @invariant {@code #getLayers() != null}
 * @invariant {@code #getGridSets() != null}
 * @invariant {@code #getVersion() != null}
 */
public class GeoWebCacheConfiguration {

    /* Attributes for parser */
    @SuppressWarnings("unused")
    private String xmlns_xsi;

    @SuppressWarnings("unused")
    private String xsi_schemaLocation;

    private String xmlns;

    /* Meta information */
    private String version;

    /* Default values */
    private Integer backendTimeout;
    
    private String lockProvider;

    private transient LockProvider lockProviderInstance;

    private Boolean cacheBypassAllowed;

    private Boolean runtimeStats;

    private String httpUsername;

    private String httpPassword;

    private String proxyUrl;

    private ServiceInformation serviceInformation;

    private List<FormatModifier> formatModifiers;

    private List<BlobStoreConfig> blobStores;

    private List<XMLGridSet> gridSets;
    
    private Boolean fullWMS;

    private Boolean wmtsCiteCompliant;

    /**
     * The persisted list of layers
     */
    private List<TileLayer> layers;
    
    /**
     * Default constructor
     */
    public GeoWebCacheConfiguration() {
        readResolve();
    }

    /**
     * XStream initialization after deserialization
     */
    private Object readResolve() {
        if (version == null) {
            setVersion("1.3.0");
        }

        xmlns_xsi = "http://www.w3.org/2001/XMLSchema-instance";

        xmlns = "http://geowebcache.org/schema/" + getVersion();

        xsi_schemaLocation = xmlns + " http://geowebcache.org/schema/" + getVersion()
                + "/geowebcache.xsd";

        if (layers == null) {
            layers = new ArrayList<TileLayer>();
        }

        if (gridSets == null) {
            gridSets = new ArrayList<XMLGridSet>();
        }
        
        if(blobStores == null){
        	blobStores = new ArrayList<>();
        }
        return this;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version
     *            the version to set
     */
    public void setVersion(String version) {
        this.version = version;
        xmlns = "http://geowebcache.org/schema/" + version;
        xsi_schemaLocation = xmlns + " http://geowebcache.org/schema/" + version
                + "/geowebcache.xsd";
    }

    /**
     * @return the backendTimeout
     */
    public Integer getBackendTimeout() {
        return backendTimeout;
    }

    /**
     * @param backendTimeout
     *            the backendTimeout to set
     */
    public void setBackendTimeout(Integer backendTimeout) {
        this.backendTimeout = backendTimeout;
    }

    /**
     * @return the cacheBypassAllowed
     */
    public Boolean getCacheBypassAllowed() {
        return cacheBypassAllowed;
    }

    /**
     * @param cacheBypassAllowed
     *            the cacheBypassAllowed to set
     */
    public void setCacheBypassAllowed(Boolean cacheBypassAllowed) {
        this.cacheBypassAllowed = cacheBypassAllowed;
    }

    /**
     * @return the runtimeStats
     */
    public Boolean getRuntimeStats() {
        return runtimeStats;
    }

    /**
     * @param runtimeStats
     *            the runtimeStats to set
     */
    public void setRuntimeStats(Boolean runtimeStats) {
        this.runtimeStats = runtimeStats;
    }

    /**
     * @return the httpUsername
     */
    public String getHttpUsername() {
        return httpUsername;
    }

    /**
     * @param httpUsername
     *            the httpUsername to set
     */
    public void setHttpUsername(String httpUsername) {
        this.httpUsername = httpUsername;
    }

    /**
     * @return the httpPassword
     */
    public String getHttpPassword() {
        return httpPassword;
    }

    /**
     * @param httpPassword
     *            the httpPassword to set
     */
    public void setHttpPassword(String httpPassword) {
        this.httpPassword = httpPassword;
    }

    /**
     * @return the proxyUrl
     */
    public String getProxyUrl() {
        return proxyUrl;
    }

    /**
     * @param proxyUrl
     *            the proxyUrl to set
     */
    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    /**
     * @see ServerConfiguration#getServiceInformation()
     */
    public ServiceInformation getServiceInformation() {
        return serviceInformation;
    }

    /**
     * @see ServerConfiguration#setServiceInformation(ServiceInformation)
     *            the serviceInformation to set
     */
    public void setServiceInformation(ServiceInformation serviceInformation) {
        this.serviceInformation = serviceInformation;
    }

    /**
     * @return the formatModifiers
     */
    public List<FormatModifier> getFormatModifiers() {
        return formatModifiers;
    }

    /**
     * @param formatModifiers
     *            the formatModifiers to set
     */
    public void setFormatModifiers(List<FormatModifier> formatModifiers) {
        this.formatModifiers = formatModifiers;
    }

    public List<BlobStoreConfig> getBlobStores(){
    	return blobStores;
    }
    
    /**
     * @return the gridSets
     */
    public List<XMLGridSet> getGridSets() {
        return gridSets;
    }

    /**
     * @return the layers
     */
    public List<TileLayer> getLayers() {
        return layers;
    }
    
    /**
     * Returns the chosen lock provider
     * @see ServerConfiguration#getLockProvider()
     * @return
     */
    public LockProvider getLockProvider() {
        if(lockProviderInstance == null) {
            if(lockProvider == null) {
                lockProviderInstance = new MemoryLockProvider();
            } else {
                Object provider = GeoWebCacheExtensions.bean(lockProvider);
                if(provider == null) {
                    throw new RuntimeException("Could not find lock provider " + lockProvider 
                            + " in the spring application context");
                } else if(!(provider instanceof LockProvider)) {
                    throw new RuntimeException("Found bean " + lockProvider 
                            + " in the spring application context, but it was not a LockProvider");
                } else {
                    lockProviderInstance = (LockProvider) provider;
                }
            }
        }
        
        return lockProviderInstance;
    }

    /**
     * Set the LockProvider is present
     * @see ServerConfiguration#setLockProvider(LockProvider)
     * @param lockProvider to set for this configuration
     */
    public void setLockProvider(LockProvider lockProvider){
        this.lockProviderInstance = lockProvider;
    }

    /**
     * Get the FullWMS value if present
     * @see ServerConfiguration#getfullWMS()
     * @return TRUE, FALSE, or NULL
     */
    public Boolean getFullWMS() {
        return fullWMS;
    }

    /**
     * Set the FullWMS value if present
     * @see ServerConfiguration#setFullWMS(boolean)
     * @param fullWMS is true or false
     */
    public void setFullWMS(Boolean fullWMS) {
        this.fullWMS = fullWMS;
    }

    /**
     * If this method returns NULL CITE strict compliance mode should not be considered for WMTS
     * service implementation.
     *
     * @return may return TRUE, FALSE or NULL
     */
    public boolean isWmtsCiteCompliant() {
        return wmtsCiteCompliant != null ? wmtsCiteCompliant : false;
    }

    /**
     * Can be used to force WMTS service implementation to be strictly compliant with the
     * correspondent CITE tests.
     *
     * @param wmtsCiteStrictCompliant TRUE or FALSE, activating or deactivation CITE
     *                                strict compliance mode for WMTS
     */
    public void setWmtsCiteCompliant(boolean wmtsCiteCompliant) {
        this.wmtsCiteCompliant = wmtsCiteCompliant;
    }
}
