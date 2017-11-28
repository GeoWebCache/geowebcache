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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */
package org.geowebcache.config;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;

/**
 * A layer and gridset provider for {@link TileLayerDispatcher}.
 * <p>
 * Implementations must be singletons discoverable through {@link GeoWebCacheExtensions} (i.e.
 * spring beans)
 * </p>
 */
public interface Configuration {

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
    public int initialize(GridSetBroker gridSetBroker) throws GeoWebCacheException;

    /**
     * @return an unmodifiable list of layers, may be empty, but not null.
     * @deprecated
     */
    public List<? extends TileLayer> getTileLayers();

    /**
     * @return an unmodifiable list of layers, may be empty, but not null.
     */
    public Iterable<? extends TileLayer> getLayers();

    /**
     * @return non null identifier for this configuration
     */
    public String getIdentifier();

    public ServiceInformation getServiceInformation();

    public boolean isRuntimeStatsEnabled();

    /**
     * @param layerName
     *            the layer name
     * @return the layer named {@code layerIdent} or {@code null} if no such layer exists in this
     *         configuration
     */
    public TileLayer getTileLayer(String layerName);

    /**
     * @param layerId
     *            the layer identifier
     * @return the layer identified by {@code layerId} or {@code null} if no such layer exists in
     *         this configuration
     */
    public TileLayer getTileLayerById(String layerId);

    public int getTileLayerCount();

    public Set<String> getTileLayerNames();

    /**
     * @param layerName
     * @return {@code true} if the layer was removed, {@code false} if no such layer exists
     */
    public boolean removeLayer(String layerName);

    public void modifyLayer(TileLayer tl) throws NoSuchElementException;

    /**
     * Saves this configuration
     * 
     * @throws IOException
     */
    public void save() throws IOException;

    /**
     * @param tl
     *            a tile layer to be added or saved
     * @return {@code true} if this configuration is capable of saving the given tile layer,
     *         {@code false} otherwise (usually this check is based on an instanceof check, as
     *         different configurations may be specialized on different kinds of layers).
     */
    public boolean canSave(TileLayer tl);

    /**
     * Adds, but not saves, the given tile layer to this configuration, provided
     * {@link #canSave(TileLayer) canSave(tl) == true}.
     * 
     * @param tl
     *            the tile layer to add to the configuration
     * @throws IllegalArgumentException
     *             if this configuration is not able of saving the specific type of layer given, or
     *             if any required piece of information is missing or invalid in the layer (for
     *             example, a missing or duplicated name or id, etc).
     */
    public void addLayer(TileLayer tl) throws IllegalArgumentException;

    public boolean containsLayer(String tileLayerId);

    /**
     * If this method returns TRUE WMTS service implementation will strictly comply with the
     * correspondent CITE tests.
     *
     * @return TRUE or FALSE, activating or deactivation CITE strict compliance mode for WMTS
     */
    default boolean isWmtsCiteCompliant() {
        // by default CITE tests strict compliance is not activated
        return false;
    }
}
