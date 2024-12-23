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
 * @author Arne Kepp / The Open Planning Project 2009
 * @author David Vick, Boundless, Copyright 2017
 *     <p>Original file GWCRestlet.java
 */
package org.geowebcache.rest.controller;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.exception.RestException;
import org.springframework.http.HttpStatus;

/** Base/utility class for MVC controllers. */
public class GWCController {
    /**
     * Gets and verifies a tile layer from the dispatcher
     *
     * @param layerName the layer name
     * @param layerDispatcher the dispatcher
     * @return The tile layer
     * @throws RestException if the layer name is null or empty, if the layer does not exist, or if an error was
     *     encountered retrieving the layer from the dispatcher.
     */
    protected static TileLayer findTileLayer(String layerName, TileLayerDispatcher layerDispatcher)
            throws RestException {
        if (layerName == null || layerName.length() == 0) {
            throw new RestException("Layer not specified", HttpStatus.BAD_REQUEST);
        }
        // GWC supports using + instead of space in layer names.
        layerName = layerName.replace("+", " ");

        if (!layerDispatcher.layerExists(layerName)) {
            throw new RestException("Unknown layer: " + layerName, HttpStatus.NOT_FOUND);
        }

        TileLayer layer;
        try {
            layer = layerDispatcher.getTileLayer(layerName);
        } catch (GeoWebCacheException gwce) {
            throw new RestException("Encountered error: " + gwce.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return layer;
    }
}
