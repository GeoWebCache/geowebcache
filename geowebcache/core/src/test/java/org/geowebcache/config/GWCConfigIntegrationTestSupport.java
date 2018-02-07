package org.geowebcache.config;

import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;

import java.io.IOException;
import java.util.List;

/**
 * Provides access to an (initially empty) GWC configuration.
 *
 * Seperate implementations exist for each of the different GWC Configurations. See:
 * * {@link GWCXMLConfigIntegrationTestSupport}
 */
public abstract class GWCConfigIntegrationTestSupport {

    GridSetBroker broker = new GridSetBroker(true, true);

    /**
     * Resets to an empty configuration;
     */
    public abstract void resetConfiguration() throws Exception;

    /**
     * @return The list of {@link TileLayerConfiguration}s for this GWC configuration (usually just a singleton list)
     */
    public abstract List<TileLayerConfiguration> getTileLayerConfigurations();

    /**
     * @return The gridset broker for this configuration
     */
    public GridSetBroker getGridSetBroker() {
        return broker;
    }

    /**
     * @return The {@link ServerConfiguration} for this configuration
     */
    public abstract ServerConfiguration getServerConfiguration();

    /**
     * @return The {@link GridSetConfiguration} for this configuration
     */
    public abstract List<GridSetConfiguration> getGridSetConfigurations();
    
    /**
     * @return The {@link GridSetConfiguration} for this configuration
     */
    public abstract GridSetConfiguration getWritableGridSetConfiguration();


    /**
     * @return The {@link BlobStoreConfiguration} for this configuration
     */
    public abstract BlobStoreConfiguration getBlobStoreConfiguration();
}
