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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.util.Configuration;

public class TileLayerDispatcher {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.TileLayerDispatcher.class);

    private volatile HashMap<String, TileLayer> layers = null;

    private List configs = null;

    public TileLayerDispatcher() {
        log.info("TileLayerFactor constructed");
    }

    public TileLayer getTileLayer(String layerIdent)
            throws GeoWebCacheException {

        HashMap<String, TileLayer> tmpLayers = this.getLayers();

        TileLayer layer = tmpLayers.get(layerIdent);
        if (layer == null) {
            throw new GeoWebCacheException("Unknown layer " + layerIdent
                    + ". Check the logfiles,"
                    + " it may not have loaded properly.");
        }

        layer.isInitialized();

        return layer;
    }

    public void setConfig(List configs) {
        this.configs = configs;
    }

    /***
     * Reinitialization is tricky, because we can't really just lock all the
     * layers, because this would cause people to queue on something that we may
     * not want to exist post reinit.
     * 
     * So we'll just set the current layer set free, ready for garbage
     * collection, and generate a new one.
     * 
     * @throws GeoWebCacheException
     */
    public void reInit() throws GeoWebCacheException {
        // Clear
        synchronized (this) {
            this.layers = null;
        }
        // And then we do it again
        getLayers();
    }

    /**
     * Returns a list of all the layers. The consumer may still have to
     * initialize each layer!
     * 
     * @return
     */
    public HashMap<String, TileLayer> getLayers() {
        HashMap<String, TileLayer> result = this.layers;
        if (result == null) {
            synchronized (this) {
                result = this.layers;
                if (result == null) {
                    this.layers = result = initialize();
                }
            }
        }
        return result;
    }

    private HashMap<String, TileLayer> initialize() {
        log.debug("Thread initLayers(), initializing");

        HashMap<String, TileLayer> layers = new HashMap<String, TileLayer>();

        Iterator<Configuration> configIter = configs.iterator();
        while (configIter.hasNext()) {
            Map<String, TileLayer> configLayers = null;

            Configuration config = configIter.next();

            String configIdent = config.getIdentifier();

            if (configIdent != null) {
                try {
                    configLayers = config.getTileLayers();
                } catch (GeoWebCacheException gwce) {
                    log.error(gwce.getMessage());
                    log.error("Failed to add layers from " + configIdent);
                }

                log.info("Adding layers from " + configIdent);
                if (configLayers != null && configLayers.size() > 0) {
                    Iterator<Entry<String, TileLayer>> iter = configLayers
                            .entrySet().iterator();
                    while (iter.hasNext()) {
                        Entry<String, TileLayer> one = iter.next();
                        layers.put(one.getKey(), one.getValue());
                    }
                } else {
                    log.error("Configuration " + configIdent
                            + " contained no layers.");
                }
            }
        }

        return layers;
    }
}
