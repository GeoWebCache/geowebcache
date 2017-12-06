package org.geowebcache.config;

import java.io.IOException;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;

public interface BaseConfiguration {

    /**
     * Initializes this configuration.
     * <p>
     * Any gridset provided by this configuration must be added to {@code gridSetBroker}
     * </p>
     * <p>
     * Any layer provided by this configuration must be {@link TileLayer#initialize(GridSetBroker)
     * initialized} with the provided {@code gridSetBroker}.
     * </p>
     * 
     * @param gridSetBroker
     * @return the count of layers provided by this configuration after initialization.
     * @throws GeoWebCacheException
     */
    int initialize(GridSetBroker gridSetBroker) throws GeoWebCacheException;

    /**
     * @return non null identifier for this configuration
     */
    String getIdentifier();


    /**
     * Saves this configuration
     * 
     * @throws IOException
     */
    void save() throws IOException;

    /**
     * @param tl
     *            a tile layer to be added or saved
     * @return {@code true} if this configuration is capable of saving the given tile layer,
     *         {@code false} otherwise (usually this check is based on an instanceof check, as
     *         different configurations may be specialized on different kinds of layers).
     */
    boolean canSave(TileLayer tl);

}