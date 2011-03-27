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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSetBroker;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * Note that the constructor starts the thread to load configurations, making this class unsuitable
 * for subclassing
 */
public class TileLayerDispatcher implements DisposableBean {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayerDispatcher.class);

    /**
     * Configured layers. Not to be accessed directly. Call {@link #checkConfigurationLoaded()} to
     * get them
     */
    private Map<String, TileLayer> configuredLayers = null;

    private List<Configuration> configs = null;

    private GridSetBroker gridSetBroker = null;

    private ServiceInformation serviceInformation = null;

    private ExecutorService configLoadService;

    private Future<Map<String, TileLayer>> configurationLoadTask;

    public TileLayerDispatcher(GridSetBroker gridSetBroker, List<Configuration> configs) {
        this(gridSetBroker, configs, 2);
    }

    public TileLayerDispatcher(GridSetBroker gridSetBroker, List<Configuration> configs,
            int loadDelay) {
        this.gridSetBroker = gridSetBroker;

        this.configs = configs;

        if (loadDelay > -1) {
            ThreadFactory tfac = new CustomizableThreadFactory("GWC Configuration loader thread-");
            ((CustomizableThreadFactory) tfac).setDaemon(true);
            configLoadService = Executors.newSingleThreadExecutor(tfac);
            ConfigurationLoader loader = new ConfigurationLoader(this, loadDelay);
            configurationLoadTask = configLoadService.submit(loader);
        } else {
            try {
                configuredLayers = new ConfigurationLoader(this, loadDelay).call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public TileLayer getTileLayer(String layerIdent) throws GeoWebCacheException {

        final Map<String, TileLayer> layers = checkConfigurationLoaded();

        TileLayer layer = layers.get(layerIdent);

        if (layer == null) {
            throw new GeoWebCacheException("Thread " + Thread.currentThread().getId()
                    + " Unknown layer " + layerIdent + ". Check the logfiles,"
                    + " it may not have loaded properly.");
        }

        return layer;
    }

    /**
     * Returns the configured layers, potentially waiting for the layer load task to finish
     * 
     * @return
     * @throws GeoWebCacheException
     */
    private Map<String, TileLayer> checkConfigurationLoaded() throws GeoWebCacheException {
        Map<String, TileLayer> layers = this.configuredLayers;
        if (layers == null) {
            try {
                layers = configurationLoadTask.get();
                this.configuredLayers = layers;
            } catch (InterruptedException e) {
                throw new GeoWebCacheException(e);
            } catch (ExecutionException e) {
                throw new GeoWebCacheException(e);
            }
        }
        return layers;
    }

    /***
     * Reinitialization is tricky, because we can't really just lock all the layers, because this
     * would cause people to queue on something that we may not want to exist post reinit.
     * 
     * So we'll just set the current layer set free, ready for garbage collection, and generate a
     * new one.
     * 
     * @throws GeoWebCacheException
     */
    public void reInit() throws GeoWebCacheException {
        // this should wait for the current running config load task to finish if it still didn't
        checkConfigurationLoaded();
        // now mark config not loaded by setting layers to null
        this.configuredLayers = null;
        // and let a new task to perform the config load
        configurationLoadTask = configLoadService.submit(new ConfigurationLoader(this, 0));
    }

    public int getLayerCount() {
        return getLayers().size();
    }

    public Set<String> getLayerNames() {
        return new HashSet<String>(getLayers().keySet());
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
        return new ArrayList<TileLayer>(getLayers().values());
    }

    /**
     * Returns a list of all the layers. The consumer may still have to initialize each layer!
     * 
     * @return
     */
    private Map<String, TileLayer> getLayers() {
        final Map<String, TileLayer> layers;
        try {
            layers = checkConfigurationLoaded();
        } catch (GeoWebCacheException e) {
            throw new IllegalStateException(e);
        }

        return layers;
    }

    private LinkedHashMap<String, TileLayer> initialize(boolean reload) {
        log.debug("Thread initLayers(), initializing");

        LinkedHashMap<String, TileLayer> newLayers = new LinkedHashMap<String, TileLayer>();

        Iterator<Configuration> configIter = configs.iterator();

        while (configIter.hasNext()) {
            List<TileLayer> configLayers = null;

            Configuration config = configIter.next();

            String configIdent = null;
            try {
                configIdent = config.getIdentifier();
            } catch (GeoWebCacheException gwce) {
                log.error(gwce.getMessage());
            }

            if (configIdent != null) {
                try {
                    // This is used by reload as well
                    configLayers = config.getTileLayers(reload);
                } catch (GeoWebCacheException gwce) {
                    log.error(gwce.getMessage());
                    log.error("Failed to add layers from " + configIdent);
                }

                log.info("Adding layers from " + configIdent);
                if (configLayers != null && configLayers.size() > 0) {
                    Iterator<TileLayer> iter = configLayers.iterator();

                    while (iter.hasNext()) {
                        TileLayer layer = iter.next();

                        if (layer == null) {
                            log.error("layer was null");
                            continue;
                        }
                        log.info("Adding: " + layer.getName());

                        layer.initialize(gridSetBroker);

                        add(layer, newLayers);
                    }
                } else {
                    log.error("Configuration " + configIdent + " contained no layers.");
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
            }
        }

        return newLayers;
    }

    public ServiceInformation getServiceInformation() {
        return this.serviceInformation;
    }

    public synchronized void update(TileLayer layer) {

        final Map<String, TileLayer> layers;
        try {
            layers = checkConfigurationLoaded();
        } catch (GeoWebCacheException e) {
            throw new IllegalStateException(e);
        }
        TileLayer oldLayer = layers.get(layer.getName());

        // Updates from GeoServer ultimately come as changes,
        // so we can't assume this layer actually existed
        if (oldLayer != null) {
            oldLayer.acquireLayerLock();
            layers.remove(layer.getName());
            oldLayer.releaseLayerLock();
        }
        layers.put(layer.getName(), layer);
    }

    public synchronized void remove(String layerName) {

        final Map<String, TileLayer> layers;
        try {
            layers = checkConfigurationLoaded();
        } catch (GeoWebCacheException e) {
            throw new IllegalStateException(e);
        }
        TileLayer layer = layers.get(layerName);
        if (layer != null) {
            layer.acquireLayerLock();
            layers.remove(layerName);
            layer.releaseLayerLock();
        }
    }

    public void add(TileLayer layer) {
        final Map<String, TileLayer> layers;
        try {
            layers = checkConfigurationLoaded();
        } catch (GeoWebCacheException e) {
            throw new IllegalStateException(e);
        }
        add(layer, layers);

    }

    private void add(TileLayer layer, Map<String, TileLayer> layerMap) {
        if (layerMap.containsKey(layer.getName())) {
            try {
                layerMap.get(layer.getName()).mergeWith(layer);
            } catch (GeoWebCacheException gwce) {
                log.error(gwce.getMessage());
            }
        } else {
            layerMap.put(layer.getName(), layer);
        }
    }

    private class ConfigurationLoader implements Callable<Map<String, TileLayer>> {

        TileLayerDispatcher parent;

        int loadDelay;

        private ConfigurationLoader(TileLayerDispatcher parent, int loadDelay) {
            this.parent = parent;
            this.loadDelay = loadDelay;
        }

        public Map<String, TileLayer> call() throws Exception {
            if (loadDelay > 0) {
                log.info("ConfigurationLoader acquired lock, sleeping " + loadDelay + " seconds");
                try {
                    Thread.sleep(loadDelay * 1000);
                    log.info("ConfigurationLoader woke up, initializing");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException("Configuration loader thread interrupted", e);
                }
            }

            LinkedHashMap<String, TileLayer> newLayers = parent.initialize(false);
            log.info("ConfigurationLoader completed");
            return newLayers;
        }

    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        if (configLoadService != null) {
            log.info("Shutting down config load service thread...");
            configLoadService.shutdownNow();
            int timeout = 10;
            boolean terminated = configLoadService.awaitTermination(timeout, TimeUnit.SECONDS);
            if (terminated) {
                log.info("Config load service shut down.");
            } else {
                log.warn("Config load service didn't terminate after "
                        + timeout
                        + " seconds. This may prevent the server container to properly shut down!!!");
            }
        }
    }

}
