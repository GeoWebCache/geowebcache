package org.geowebcache.layer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.geowebcache.conveyor.ConveyorTile;

/**
 * Helper class for a {@link TileLayer} to maintain and dispatch events
 * 
 * @author groldan
 * 
 */
public class LayerListenerList {

    private List<TileLayerListener> listeners = new CopyOnWriteArrayList<TileLayerListener>();

    public synchronized void addListener(TileLayerListener listener) {
        if (listener != null) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public synchronized boolean removeListener(TileLayerListener listener) {
        return listeners.remove(listener);
    }

    public void sendTileRequested(TileLayer layer, ConveyorTile tile) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).tileRequested(layer, tile);
            }
        }
    }
}
