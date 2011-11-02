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
import java.util.NoSuchElementException;
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
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.Assert;

import com.sun.org.apache.xerces.internal.xni.parser.XMLConfigurationException;

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
     * Returns the layer named after the {@code layerIdent} parameter.
     * 
     * @throws GeoWebCacheException
     *             if no such layer exists
     */
    public TileLayer getTileLayer(final String layerIdent) throws GeoWebCacheException {

        for (int i = 0; i < configs.size(); i++) {
            Configuration configuration = configs.get(i);
            TileLayer layer = configuration.getTileLayer(layerIdent);
            if (layer != null) {
                return layer;
            }
        }
        throw new GeoWebCacheException("Thread " + Thread.currentThread().getId()
                + " Unknown layer " + layerIdent + ". Check the logfiles,"
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
    public Iterable<TileLayer> getLayerList() {
        ArrayList<TileLayer> layers = new ArrayList<TileLayer>();
        for (int i = 0; i < configs.size(); i++) {
            Configuration configuration = configs.get(i);
            layers.addAll(configuration.getTileLayers());
        }
        return layers;
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

    public boolean removeLayer(final String layerName) throws IOException {
        for (Configuration config : configs) {
            if (config.removeLayer(layerName)) {
                config.save();
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces and saves the given layer
     * 
     * @param tl
     * @throws NoSuchElementException
     * @throws IOException
     */
    public synchronized void modify(final TileLayer tl) throws NoSuchElementException, IOException {
        if (!layerExists(tl.getName())) {
            throw new NoSuchElementException("No layer named " + tl.getName() + " exists");
        }
        Configuration config = getConfiguration(tl);
        config.modifyLayer(tl);
        config.save();
    }

    private Configuration getConfiguration(TileLayer tl) {
        for (Configuration c : configs) {
            if (null != c.getTileLayer(tl.getName())) {
                return c;
            }
        }
        return null;
    }

    /**
     * Eliminates the gridset from the {@link GridSetBroker} and the
     * {@link XMLConfigurationException} and saves the configuration, only if no layer references
     * the given GridSet.
     * 
     * @param gridSetName
     *            the gridset to remove.
     * @return the removed gridset
     * @throws IllegalStateException
     *             if there's any layer referencing the given GridSet
     * @throws IOException
     */
    public synchronized GridSet removeGridset(final String gridSetName)
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
        persistingConfig.save();
        return removed;
    }

    public synchronized void addGridSet(final GridSet gridSet) throws IllegalArgumentException,
            IOException {
        if (null != gridSetBroker.get(gridSet.getName())) {
            throw new IllegalArgumentException("GridSet " + gridSet.getName() + " already exists");
        }
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
