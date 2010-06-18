package org.geowebcache.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.geowebcache.conveyor.ConveyorTile;

/**
 * Helper class for a {@link TileLayer} to maintain and dispatch events
 * 
 * @author groldan
 * 
 */
public class LayerListenerList {

    private List<TileLayerListener> listeners = Collections.emptyList();

    public void addListener(TileLayerListener listener) {
        if (listener != null) {
            ArrayList<TileLayerListener> tmp;
            tmp = new ArrayList<TileLayerListener>(listeners.size() + 1);
            tmp.addAll(listeners);
            tmp.add(listener);
            listeners = tmp;
        }
    }

    public boolean removeListener(TileLayerListener listener) {
        return listeners.remove(listener);
    }

    public void sendTileRequested(TileLayer layer, ConveyorTile tile) {
        if (listeners.size() == 0) {
            return;
        }
        TileLayerListener listener;
        for (int i = 0; i < listeners.size(); i++) {
            listener = listeners.get(i);
            listener.tileRequested(layer, tile);
        }
    }
}
