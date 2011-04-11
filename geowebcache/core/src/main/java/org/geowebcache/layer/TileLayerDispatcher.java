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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSetBroker;
import org.springframework.beans.factory.DisposableBean;

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
        try {
            for (int i = 0; i < configs.size(); i++) {
                Configuration configuration = configs.get(i);
                for (TileLayer tl : configuration.getTileLayers()) {
                    names.add(tl.getName());
                }
            }
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
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
    public List<TileLayer> getLayerList() {
        ArrayList<TileLayer> layers = new ArrayList<TileLayer>();
        for (int i = 0; i < configs.size(); i++) {
            Configuration configuration = configs.get(i);
            try {
                layers.addAll(configuration.getTileLayers());
            } catch (GeoWebCacheException e) {
                throw new RuntimeException(e);
            }
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
            log.error("Configuration " + config.getIdentifier() + " contained no layers.");
        }

        // Check whether there is any general service information
        if (this.serviceInformation == null) {
            try {
                log.debug("Reading service information.");
                this.serviceInformation = config.getServiceInformation();
            } catch (GeoWebCacheException e) {
                log.error("Error reading service information from " + configIdent + ": "
                        + e.getMessage());
            }
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

    public boolean remove(final String layerName) {
        for (Configuration config : configs) {
            if (config.remove(layerName)) {
                return true;
            }
        }
        return false;
    }

}
