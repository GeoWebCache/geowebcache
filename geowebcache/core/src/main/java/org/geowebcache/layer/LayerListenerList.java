package org.geowebcache.layer;

import java.util.ArrayList;
import java.util.List;

import org.geowebcache.conveyor.ConveyorTile;

/**
 * Helper class for a {@link TileLayer} to maintain and dispatch events
 * 
 * @author groldan
 * 
 */
public class LayerListenerList {

    private List<TileLayerListener> listeners;

    public void addListener(TileLayerListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<TileLayerListener>(2);
        }
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public boolean removeListener(TileLayerListener listener) {
        return listeners == null ? false : listeners.remove(listener);
    }

    public void sendTileRequested(TileLayer layer, ConveyorTile tile) {
        if (listeners == null || listeners.size() == 0) {
            return;
        }
        TileLayerListener listener;
        for (int i = 0; i < listeners.size(); i++) {
            listener = listeners.get(i);
            listener.tileRequested(layer, tile);
        }
    }

    public void sendTileSeeded(TileLayer layer, ConveyorTile tile) {
        if (listeners == null || listeners.size() == 0) {
            return;
        }
        TileLayerListener listener;
        for (int i = 0; i < listeners.size(); i++) {
            listener = listeners.get(i);
            listener.tileSeeded(layer, tile);
        }
    }

}
