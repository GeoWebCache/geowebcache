/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.layer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.geowebcache.conveyor.ConveyorTile;

/**
 * Helper class for a {@link TileLayer} to maintain and dispatch events
 *
 * @author groldan
 */
public class LayerListenerList {

    private List<TileLayerListener> listeners = new CopyOnWriteArrayList<>();

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
        if (!listeners.isEmpty()) {
            for (TileLayerListener listener : listeners) {
                listener.tileRequested(layer, tile);
            }
        }
    }
}
