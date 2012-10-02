package org.geowebcache.storage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BlobStoreListenerList {

    private List<BlobStoreListener> listeners = new CopyOnWriteArrayList<BlobStoreListener>();

    public synchronized void addListener(BlobStoreListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized boolean removeListener(BlobStoreListener listener) {
        return listeners.remove(listener);
    }

    public void sendLayerDeleted(String layerName) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).layerDeleted(layerName);
            }
        }
    }

    public void sendLayerRenamed(String oldLayerName, String newLayerName) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).layerRenamed(oldLayerName, newLayerName);
            }
        }
    }

    public void sendGridSubsetDeleted(String layerName, String gridSetId) {
        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).gridSubsetDeleted(layerName, gridSetId);
            }
        }
    }

    public void sendTileDeleted(String layerName, String gridSetId, String blobFormat,
            String parametersId, long x, long y, int z, long length) {

        if (listeners.size() > 0) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).tileDeleted(layerName, gridSetId, blobFormat, parametersId, x, y,
                        z, length);
            }
        }
    }

    public void sendTileDeleted(final TileObject stObj) {
        if (listeners.size() > 0) {

            final long[] xyz = stObj.getXYZ();
            final String layerName = stObj.getLayerName();
            final String gridSetId = stObj.getGridSetId();
            final String blobFormat = stObj.getBlobFormat();
            final String paramsId = stObj.getParametersId();
            final int blobSize = stObj.getBlobSize();

            sendTileDeleted(layerName, gridSetId, blobFormat, paramsId, xyz[0], xyz[1],
                    (int) xyz[2], blobSize);
        }
    }

    public void sendTileStored(TileObject stObj) {
        if (listeners.size() > 0) {

            final long[] xyz = stObj.getXYZ();
            final String layerName = stObj.getLayerName();
            final String gridSetId = stObj.getGridSetId();
            final String blobFormat = stObj.getBlobFormat();
            final String paramsId = stObj.getParametersId();
            final int blobSize = stObj.getBlobSize();

            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).tileStored(layerName, gridSetId, blobFormat, paramsId, xyz[0],
                        xyz[1], (int) xyz[2], blobSize);

            }
        }
    }

    public void sendTileUpdated(TileObject stObj, final long oldSize) {
        if (listeners.size() > 0) {
            final long[] xyz = stObj.getXYZ();
            final String layerName = stObj.getLayerName();
            final String gridSetId = stObj.getGridSetId();
            final String blobFormat = stObj.getBlobFormat();
            final String paramsId = stObj.getParametersId();

            final int blobSize = stObj.getBlobSize();

            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).tileUpdated(layerName, gridSetId, blobFormat, paramsId, xyz[0],
                        xyz[1], (int) xyz[2], blobSize, oldSize);

            }
        }
    }
}
