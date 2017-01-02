package org.geowebcache.config;

import java.util.List;

import org.geowebcache.layer.TileLayer;
import org.geowebcache.locks.LockProvider;

import com.thoughtworks.xstream.XStream;

/**
 * Interface for configuration of layers and gridsets
 * 
 * @author ez
 *
 */
public interface ConfigurationDispatcher extends Configuration {

    /**
     * Returns location of configuration
     * 
     * @return URI
     * @throws ConfigurationException
     */
    public String getConfigLocation() throws ConfigurationException;

    /**
     * Configuration objects lacking their own defaults can delegate to this
     * 
     * @param layer
     */
    public void setDefaultValues(TileLayer layer);

    /**
     * 
     * @param xs
     * @param providerContext
     * @return
     */
    public XStream getConfiguredXStreamWithContext(XStream xs,
            ContextualConfigurationProvider.Context providerContext);

    /**
     * @param gridSet
     * @throws IllegalArgumentException
     */
    public void addOrReplaceGridSet(final XMLGridSet gridSet) throws IllegalArgumentException;

    /**
     * @param gridsetName
     * @return the removed gridset or null
     */
    public XMLGridSet removeGridset(final String gridsetName);

    /**
     * @return the "fullWMS" parameter from GeoWebCacheConfigration
     */
    public Boolean getfullWMS();

    /**
     * @return
     */
    public List<BlobStoreConfig> getBlobStores();

    /**
     * @return
     */
    public LockProvider getLockProvider();

}
