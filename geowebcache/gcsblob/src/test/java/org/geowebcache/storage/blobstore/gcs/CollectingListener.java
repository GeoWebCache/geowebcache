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
 * @author Gabriel Roldan, Camptocamp, Copyright 2025
 */
package org.geowebcache.storage.blobstore.gcs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStoreListener;

class CollectingListener implements BlobStoreListener {

    Map<TileLocation, Long> tilesStored = new ConcurrentHashMap<>();
    Map<TileLocation, Long> tilesDeleted = new ConcurrentHashMap<>();
    Map<TileLocation, Long> tilesUpdated = new ConcurrentHashMap<>();

    Map<String, String> layersRenamed = new ConcurrentHashMap<>();

    List<String> layersDeleted = new CopyOnWriteArrayList<>();
    Map<String, List<String>> gridSubsetsDeleted = new ConcurrentHashMap<>();
    Map<String, List<String>> parametersDeleted = new ConcurrentHashMap<>();

    @Override
    public void tileStored(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize) {
        add(tilesStored, layerName, gridSetId, blobFormat, parametersId, x, y, z, blobSize);
    }

    @Override
    public void tileDeleted(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize) {
        add(tilesDeleted, layerName, gridSetId, blobFormat, parametersId, x, y, z, blobSize);
    }

    @Override
    public void tileUpdated(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize,
            long oldSize) {
        add(tilesUpdated, layerName, gridSetId, blobFormat, parametersId, x, y, z, blobSize);
    }

    private void add(
            Map<TileLocation, Long> target,
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize) {

        MimeType format;
        try {
            format = MimeType.createFromFormat(blobFormat);
        } catch (MimeException e) {
            throw new IllegalArgumentException(e);
        }

        CacheId cache = new CacheId(layerName, layerName, gridSetId, format, parametersId);
        TileIndex index = new TileIndex(x, y, z);
        TileLocation tileLocation = new TileLocation(null, cache, index);
        target.put(tileLocation, blobSize);
    }

    @Override
    public void layerDeleted(String layerName) {
        layersDeleted.add(layerName);
    }

    @Override
    public void layerRenamed(String oldLayerName, String newLayerName) {
        layersRenamed.put(oldLayerName, newLayerName);
    }

    @Override
    public void gridSubsetDeleted(String layerName, String gridSetId) {
        gridSubsetsDeleted
                .computeIfAbsent(layerName, l -> new CopyOnWriteArrayList<>())
                .add(gridSetId);
    }

    @Override
    public void parametersDeleted(String layerName, String parametersId) {
        parametersDeleted
                .computeIfAbsent(layerName, l -> new CopyOnWriteArrayList<>())
                .add(parametersId);
    }
}
