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
 */
package org.geowebcache.layer;

import java.io.IOException;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.BaseConfiguration;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.ServerConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLGridSet;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.util.CompositeIterable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.Assert;

import com.google.common.base.Preconditions;

/**
 * Serves tile layers from the {@link TileLayerConfiguration}s available in the application context.
 */
public class TileLayerDispatcher implements DisposableBean {

    private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayerDispatcher.class);

    private List<TileLayerConfiguration> configs;

    private GridSetBroker gridSetBroker;

    private ServiceInformation serviceInformation;

    /**
     * @deprecated use {@link #TileLayerDispatcher(GridSetBroker)} instead, configurations are
     *             loaded from the application context, this {@code config} parameter will be
     *             ignored
     */
    public TileLayerDispatcher(GridSetBroker gridSetBroker, List<TileLayerConfiguration> configs) {
        this.gridSetBroker = gridSetBroker;
        this.configs = configs == null ? new ArrayList<TileLayerConfiguration>() : configs;
        initialize();
    }

    public TileLayerDispatcher(GridSetBroker gridSetBroker) {
        this.gridSetBroker = gridSetBroker;
        reInit();
    }

    public void addConfiguration(TileLayerConfiguration config) {
        initialize(config);
        List<TileLayerConfiguration> newList = new ArrayList<TileLayerConfiguration>(configs);
        newList.add(config);
        this.configs = newList;
    }

    public boolean layerExists(final String layerName) {
        for (int i = 0; i < configs.size(); i++) {
            TileLayerConfiguration configuration = configs.get(i);
            TileLayer layer = configuration.getLayer(layerName);
            if (layer != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the layer named after the {@code layerName} parameter.
     * 
     * @throws GeoWebCacheException
     *             if no such layer exists
     */
    public TileLayer getTileLayer(final String layerName) throws GeoWebCacheException {
        Preconditions.checkNotNull(layerName, "layerName is null");

        for (int i = 0; i < configs.size(); i++) {
            TileLayerConfiguration configuration = configs.get(i);
            TileLayer layer = configuration.getLayer(layerName);
            if (layer != null) {
                return layer;
            }
        }
        throw new GeoWebCacheException("Thread " + Thread.currentThread().getId()
                + " Unknown layer " + layerName + ". Check the logfiles,"
                + " it may not have loaded properly.");
    }

    /***
     * Reinitialization is tricky, because we can't really just lock all the layers, because this
     * would cause people to queue on something that we may not want to exist post reinit.
     * 
     * So we'll just set the current layer set free, ready for garbage collection, and generate a
     * new one.
     * 
     */
    public void reInit() {
        List<TileLayerConfiguration> extensions = GeoWebCacheExtensions.extensions(TileLayerConfiguration.class);
        this.configs = new ArrayList<TileLayerConfiguration>(extensions);
        initialize();
    }

    public int getLayerCount() {
        int count = 0;
        for (int i = 0; i < configs.size(); i++) {
            TileLayerConfiguration configuration = configs.get(i);
            count += configuration.getLayerCount();
        }
        return count;
    }

    public Set<String> getLayerNames() {
        Set<String> names = new HashSet<String>();
        for (int i = 0; i < configs.size(); i++) {
            TileLayerConfiguration configuration = configs.get(i);
            names.addAll(configuration.getLayerNames());
        }
        return names;
    }

    /**
     * Returns a list of all the layers. The consumer may still have to initialize each layer!
     * <p>
     * Modifications to the returned layer do not change the internal list of layers, but layers ARE
     * mutable.
     * </p>
     * 
     * @return a list view of this tile layer dispatcher's internal layers
     */
    @SuppressWarnings("unchecked")
    public Iterable<TileLayer> getLayerList() {
        List<Iterable<TileLayer>> perConfigLayers = new ArrayList<Iterable<TileLayer>>(
                configs.size());

        for (TileLayerConfiguration config : configs) {
            perConfigLayers.add((Iterable<TileLayer>) config.getLayers());
        }

        return new CompositeIterable<TileLayer>(perConfigLayers);
    }

    private void initialize() {
        log.debug("Thread initLayers(), initializing");

        for (TileLayerConfiguration config : configs) {
            initialize(config);
        }
    }

    private int initialize(TileLayerConfiguration config) {
        if (config == null) {
            throw new IllegalStateException(
                    "TileLayerDispatcher got a null GWC configuration object");
        }

        String configIdent = null;
        try {
            configIdent = config.getIdentifier();
        } catch (Exception gwce) {
            log.error("Error obtaining identify from TileLayerConfiguration " + config, gwce);
            return 0;
        }

        if (configIdent == null) {
            log.warn("Got a GWC configuration with no identity, ignoring it:" + config);
            return 0;
        }

        int layerCount;
        try {
            layerCount = config.initialize(gridSetBroker);
        } catch (GeoWebCacheException gwce) {
            log.error("Failed to add layers from " + configIdent, gwce);
            return 0;
        }
        if (layerCount <= 0) {
            log.info("TileLayerConfiguration " + config.getIdentifier() + " contained no layers.");
        }

        // Check whether there is any general service information
        if (this.serviceInformation == null && config instanceof ServerConfiguration) {
            log.debug("Reading service information.");
            this.serviceInformation = ((ServerConfiguration) config).getServiceInformation();
        }
        return layerCount;
    }

    public ServiceInformation getServiceInformation() {
        return this.serviceInformation;
    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        //
    }

    /**
     * Finds out which {@link TileLayerConfiguration} contains the given layer, removes it, and saves the configuration.
     * 
     * @param layerName the name of the layer to remove
     * @return true if the layer was removed, false if it was not found.
     */
    public synchronized boolean removeLayer(final String layerName)
            throws IllegalArgumentException {
        for (TileLayerConfiguration config : configs) {
            if (config.removeLayer(layerName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a layer and returns the {@link TileLayerConfiguration} to which the layer was added.
     * 
     * @param tl the layer to add
     * @throws IllegalArgumentException if the given tile layer can't be added to any configuration managed by this tile
     *         layer dispatcher.
     */
    public synchronized void addLayer(final TileLayer tl) throws IllegalArgumentException {
        for (TileLayerConfiguration c : configs) {
            if (c.canSave(tl)) {
                c.addLayer(tl);
                return;
            }
        }
        throw new IllegalArgumentException("No configuration found capable of saving " + tl);
    }

    /**
     * Renames an existing layer.
     * 
     * @param oldName The name of the existing layer
     * @param newName The name to rename the layer to
     * @throws IllegalArgumentException
     */
    public synchronized void rename(final String oldName, final String newName) throws NoSuchElementException, IllegalArgumentException {
        TileLayerConfiguration config = getConfiguration(oldName);
        config.renameLayer(oldName, newName);
    }

    /**
     * Replaces the given layer and returns the Layer's configuration, saving the configuration.
     *
     * @param tl The layer to modify
     * @throws IllegalArgumentException
     */
    public synchronized void modify(final TileLayer tl) throws IllegalArgumentException {
        TileLayerConfiguration config = getConfiguration(tl);
        //TODO: this won't work with GetCapabilitiesConfiguration
        config.modifyLayer(tl);
    }

    public TileLayerConfiguration getConfiguration(TileLayer tl) throws IllegalArgumentException {
        Assert.notNull(tl, "layer is null");
        return getConfiguration(tl.getName());
    }

    public TileLayerConfiguration getConfiguration(final String tileLayerName) throws IllegalArgumentException {
        Assert.notNull(tileLayerName, "tileLayerName is null");
        for (TileLayerConfiguration c : configs) {
            if (c.containsLayer(tileLayerName)) {
                return c;
            }
        }
        throw new IllegalArgumentException("No configuration found containing layer " + tileLayerName);
    }

    /**
     * Eliminates the gridset from the {@link GridSetBroker} and the {@link XMLConfiguration} only
     * if no layer references the given GridSet.
     * <p>
     * NOTE this method does not save the configuration, it's up to the calling code to do that in
     * order to make the change persistent.
     * </p>
     * 
     * @param gridSetName
     *            the gridset to remove.
     * @return the configuration modified after removing the gridset, or {@code null}
     * @throws IllegalStateException
     *             if there's any layer referencing the given GridSet
     * @throws IOException
     * @see {@link GridSetBroker#remove(String)}
     */
    public synchronized TileLayerConfiguration removeGridset(final String gridSetName)
            throws IllegalStateException, IOException {

        GridSet gridSet = gridSetBroker.get(gridSetName);
        if (gridSet == null) {
            return null;
        }
        List<String> refereningLayers = new ArrayList<String>();
        for (TileLayer layer : getLayerList()) {
            GridSubset gridSubset = layer.getGridSubset(gridSetName);
            if (gridSubset != null) {
                refereningLayers.add(layer.getName());
            }
        }
        if (refereningLayers.size() > 0) {
            throw new IllegalStateException("There are TileLayers referencing gridset '"
                    + gridSetName + "': " + refereningLayers.toString());
        }
        XMLConfiguration persistingConfig = getXmlConfiguration();
        GridSet removed = gridSetBroker.remove(gridSetName);
        Assert.notNull(removed != null);
        Assert.notNull(persistingConfig.removeGridset(gridSetName));

        return persistingConfig;
    }

    public synchronized void addGridSet(final GridSet gridSet) throws IllegalArgumentException,
            IOException {
        if (null != gridSetBroker.get(gridSet.getName())) {
            throw new IllegalArgumentException("GridSet " + gridSet.getName() + " already exists");
        }
        saveGridSet(gridSet);
    }

    private void saveGridSet(final GridSet gridSet) throws IOException {
        XMLConfiguration persistingConfig = getXmlConfiguration();
        persistingConfig.addOrReplaceGridSet(new XMLGridSet(gridSet));
        persistingConfig.save();
        gridSetBroker.put(gridSet);
    }

    private XMLConfiguration getXmlConfiguration() throws IllegalStateException {
        for (BaseConfiguration c : configs) {
            if (c instanceof XMLConfiguration) {
                return (XMLConfiguration) c;
            }
        }
        throw new IllegalStateException("Found no configuration of type "
                + XMLConfiguration.class.getName());
    }
}
