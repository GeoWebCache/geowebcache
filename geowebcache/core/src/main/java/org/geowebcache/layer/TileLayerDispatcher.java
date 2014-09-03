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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.Configuration;
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
 * Serves tile layers from the {@link Configuration}s available in the application context.
 */
public class TileLayerDispatcher implements DisposableBean {

    private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayerDispatcher.class);

    private List<Configuration> configs;

    private GridSetBroker gridSetBroker;

    private ServiceInformation serviceInformation;

    /**
     * @deprecated use {@link #TileLayerDispatcher(GridSetBroker)} instead, configurations are
     *             loaded from the application context, this {@code config} parameter will be
     *             ignored
     */
    public TileLayerDispatcher(GridSetBroker gridSetBroker, List<Configuration> configs) {
        this.gridSetBroker = gridSetBroker;
        this.configs = configs == null ? new ArrayList<Configuration>() : configs;
        initialize();
    }

    public TileLayerDispatcher(GridSetBroker gridSetBroker) {
        this.gridSetBroker = gridSetBroker;
        reInit();
    }

    public void addConfiguration(Configuration config) {
        initialize(config);
        List<Configuration> newList = new ArrayList<Configuration>(configs);
        newList.add(config);
        this.configs = newList;
    }

    public boolean layerExists(final String layerName) {
        for (int i = 0; i < configs.size(); i++) {
            Configuration configuration = configs.get(i);
            TileLayer layer = configuration.getTileLayer(layerName);
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
            Configuration configuration = configs.get(i);
            TileLayer layer = configuration.getTileLayer(layerName);
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
        List<Configuration> extensions = GeoWebCacheExtensions.extensions(Configuration.class);
        this.configs = new ArrayList<Configuration>(extensions);
        initialize();
    }

    public int getLayerCount() {
        int count = 0;
        for (int i = 0; i < configs.size(); i++) {
            Configuration configuration = configs.get(i);
            count += configuration.getTileLayerCount();
        }
        return count;
    }

    public Set<String> getLayerNames() {
        Set<String> names = new HashSet<String>();
        for (int i = 0; i < configs.size(); i++) {
            Configuration configuration = configs.get(i);
            names.addAll(configuration.getTileLayerNames());
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

        for (Configuration config : configs) {
            perConfigLayers.add((Iterable<TileLayer>) config.getLayers());
        }

        return new CompositeIterable<TileLayer>(perConfigLayers);
    }

    private void initialize() {
        log.debug("Thread initLayers(), initializing");

        for (Configuration config : configs) {
            initialize(config);
        }
    }

    private int initialize(Configuration config) {
        if (config == null) {
            throw new IllegalStateException(
                    "TileLayerDispatcher got a null GWC configuration object");
        }

        String configIdent = null;
        try {
            configIdent = config.getIdentifier();
        } catch (Exception gwce) {
            log.error("Error obtaining identify from Configuration " + config, gwce);
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
            log.info("Configuration " + config.getIdentifier() + " contained no layers.");
        }

        // Check whether there is any general service information
        if (this.serviceInformation == null) {
            log.debug("Reading service information.");
            this.serviceInformation = config.getServiceInformation();
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
     * Finds out which {@link Configuration} contains the given layer,
     * {@link Configuration#removeLayer(String) removes} it, and returns the configuration, without
     * saving it.
     * <p>
     * The calling code is responsible from calling {@link Configuration#save()} on the returned
     * configuration object if the change is to be made persistent.
     * </p>
     * 
     * @param layerName
     *            the name of the layer to remove
     * @return the Configuration from which the layer has been removed, or {@code null} if no
     *         configuration contained such a layer
     */
    public synchronized Configuration removeLayer(final String layerName)
            throws IllegalArgumentException {
        for (Configuration config : configs) {
            if (config.removeLayer(layerName)) {
                return config;
            }
        }
        return null;
    }

    /**
     * Adds a layer and returns (but doesn't save) the {@link Configuration} to which the layer was
     * added.
     * 
     * @param tl
     *            the layer to add
     * @return the configuration to which the layer was added; calling code is in charge of decising
     *         whether to {@link Configuration#save() save} the configuration permanently or not.
     * @throws IllegalArgumentException
     *             if the given tile layer can't be added to any configuraion managed by this tile
     *             layer dispatcher.
     */
    public synchronized Configuration addLayer(final TileLayer tl) throws IllegalArgumentException {
        for (Configuration c : configs) {
            if (c.canSave(tl)) {
                c.addLayer(tl);
                return c;
            }
        }
        throw new IllegalArgumentException("No configuration found capable of saving " + tl);
    }

    /**
     * Replaces the given layer and returns the Layer's configuration, does not save the
     * configuration, the calling code shall do that if the change is to be made persistent.
     * 
     * @param tl
     * @throws IllegalArgumentException
     */
    public synchronized Configuration modify(final TileLayer tl) throws IllegalArgumentException {
        Configuration config = getConfiguration(tl);
        config.modifyLayer(tl);
        return config;
    }

    public Configuration getConfiguration(TileLayer tl) throws IllegalArgumentException {
        Assert.notNull(tl, "layer is null");
        return getConfiguration(tl.getId());
    }

    public Configuration getConfiguration(final String tileLayerId) throws IllegalArgumentException {
        Assert.notNull(tileLayerId, "tileLayerId is null");
        for (Configuration c : configs) {
            if (c.containsLayer(tileLayerId)) {
                return c;
            }
        }
        throw new IllegalArgumentException("No configuration found containing layer " + tileLayerId);
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
    public synchronized Configuration removeGridset(final String gridSetName)
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
        for (Configuration c : configs) {
            if (c instanceof XMLConfiguration) {
                return (XMLConfiguration) c;
            }
        }
        throw new IllegalStateException("Found no configuration of type "
                + XMLConfiguration.class.getName());
    }
}
