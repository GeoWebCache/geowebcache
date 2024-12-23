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
package org.geowebcache.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BlobStoreListenerList {

    private List<BlobStoreListener> listeners = new CopyOnWriteArrayList<>();

    public Iterable<BlobStoreListener> getListeners() {
        return new ArrayList<>(listeners);
    }

    public boolean isEmpty() {
        return listeners.isEmpty();
    }

    public synchronized void addListener(BlobStoreListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized boolean removeListener(BlobStoreListener listener) {
        return listeners.remove(listener);
    }

    public void sendLayerDeleted(String layerName) {
        listeners.forEach(listener -> {
            listener.layerDeleted(layerName);
        });
    }

    public void sendLayerRenamed(String oldLayerName, String newLayerName) {
        listeners.forEach(listener -> {
            listener.layerRenamed(oldLayerName, newLayerName);
        });
    }

    public void sendGridSubsetDeleted(String layerName, String gridSetId) {
        listeners.forEach(listener -> {
            listener.gridSubsetDeleted(layerName, gridSetId);
        });
    }

    public void sendParametersDeleted(String layerName, String parametersId) {
        listeners.forEach(listener -> {
            listener.parametersDeleted(layerName, parametersId);
        });
    }

    public void sendTileDeleted(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long length) {
        listeners.forEach(listener -> {
            listener.tileDeleted(layerName, gridSetId, blobFormat, parametersId, x, y, z, length);
        });
    }

    public void sendTileDeleted(final TileObject stObj) {

        final long[] xyz = stObj.getXYZ();
        final String layerName = stObj.getLayerName();
        final String gridSetId = stObj.getGridSetId();
        final String blobFormat = stObj.getBlobFormat();
        final String paramsId = stObj.getParametersId();
        final int blobSize = stObj.getBlobSize();

        sendTileDeleted(layerName, gridSetId, blobFormat, paramsId, xyz[0], xyz[1], (int) xyz[2], blobSize);
    }

    public void sendTileStored(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long length) {
        listeners.forEach(listener -> {
            listener.tileStored(layerName, gridSetId, blobFormat, parametersId, x, y, z, length);
        });
    }

    public void sendTileStored(final TileObject stObj) {

        final long[] xyz = stObj.getXYZ();
        final String layerName = stObj.getLayerName();
        final String gridSetId = stObj.getGridSetId();
        final String blobFormat = stObj.getBlobFormat();
        final String paramsId = stObj.getParametersId();
        final int blobSize = stObj.getBlobSize();

        sendTileStored(layerName, gridSetId, blobFormat, paramsId, xyz[0], xyz[1], (int) xyz[2], blobSize);
    }

    public void sendTileUpdated(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize,
            long oldSize) {
        listeners.forEach(listener -> {
            listener.tileUpdated(layerName, gridSetId, blobFormat, parametersId, x, y, z, blobSize, oldSize);
        });
    }

    public void sendTileUpdated(final TileObject stObj, final long oldSize) {

        final long[] xyz = stObj.getXYZ();
        final String layerName = stObj.getLayerName();
        final String gridSetId = stObj.getGridSetId();
        final String blobFormat = stObj.getBlobFormat();
        final String paramsId = stObj.getParametersId();
        final int blobSize = stObj.getBlobSize();

        sendTileUpdated(layerName, gridSetId, blobFormat, paramsId, xyz[0], xyz[1], (int) xyz[2], blobSize, oldSize);
    }
}
