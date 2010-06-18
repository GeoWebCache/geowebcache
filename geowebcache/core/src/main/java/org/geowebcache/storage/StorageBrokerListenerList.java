package org.geowebcache.storage;

import java.util.ArrayList;
import java.util.List;

final class StorageBrokerListenerList {

    private List<StorageBrokerListener> listeners = new ArrayList<StorageBrokerListener>(1);

    public synchronized void addListener(StorageBrokerListener listener) {
        if (listener != null) {
            List<StorageBrokerListener> tmp;
            tmp = new ArrayList<StorageBrokerListener>(listeners.size() + 1);
            tmp.add(listener);
            listeners = tmp;
        }
    }

    public synchronized boolean removeListener(StorageBrokerListener listener) {
        return listeners.remove(listener);
    }

    public void sendLayerDeleted(String layerName) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).layerDeleted(layerName);
            }
        }
    }

    public void setTileRangeDeleted(TileRange tileRange) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).tileRangeDeleted(tileRange);
            }
        }
    }

    public void sendTileRangeExpired(TileRange tileRange) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).tileRangeExpired(tileRange);
            }
        }
    }

    public void sendCacheHit(TileObject tileObj) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).cacheHit(tileObj);
            }
        }
    }

    public void sendCacheMiss(TileObject tileObj) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).cacheMiss(tileObj);
            }
        }
    }

    public void sendTileCached(TileObject tileObj) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).tileCached(tileObj);
            }
        }
    }

    public void sendShutDownEvent() {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).shutDown();
            }
        }
    }
}
